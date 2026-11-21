package dev.recipeeditor.app.project;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Single versioned project file, integrity hash, previous-good backup and replace-on-success. */
public final class FileProjectPersistence implements ProjectPersistence {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("project-storage-",0).factory());
    private final ObjectMapper mapper=new ObjectMapper();
    private final Map<Integer,ProjectFormatMigration> migrations;
    public FileProjectPersistence() {this(List.of());}
    public FileProjectPersistence(final List<ProjectFormatMigration> migrations) {
        final var indexed=new HashMap<Integer,ProjectFormatMigration>();
        for(final var migration:migrations) {
            if(migration.fromVersion()<0 || migration.toVersion()<=migration.fromVersion() || migration.toVersion()>1 || indexed.putIfAbsent(migration.fromVersion(),migration)!=null)
                throw new IllegalArgumentException("Invalid or ambiguous project migration");
        }
        this.migrations=Map.copyOf(indexed);
    }
    @Override public CompletableFuture<ProjectDocument> load(final Path file) { return CompletableFuture.supplyAsync(()->{ try { return read(file); } catch(IOException error) { throw new CompletionException(error); } },worker); }
    @Override public CompletableFuture<Void> save(final Path file,final ProjectDocument project) { return CompletableFuture.runAsync(()->{ try { write(file,project); } catch(IOException error) { throw new CompletionException(error); } },worker); }
    public ProjectDocument read(final Path file) throws IOException {
        if(!Files.isRegularFile(file) || Files.size(file)>48L*1024*1024) throw new IOException("Invalid or oversized project file");
        try(final var input=Files.newInputStream(file)) {
            final String raw=new String(SafeFiles.read(input,48*1024*1024),StandardCharsets.UTF_8);
            final var root=new ResourceJsonReader().read(raw);
            if(!root.isObject() || !root.path("formatVersion").isIntegralNumber()) throw new IOException("Project format version is missing");
            int version=root.path("formatVersion").asInt(-1);
            JsonNode payload=root.path("project");
            if(!root.path("sha256").asText().equals(hash(payload))) throw new IOException("Project integrity check failed");
            while(version!=1) {
                final var migration=migrations.get(version);
                if(migration==null) throw new IOException("Unsupported project format version: "+version+" (supported: 1; no registered migration)");
                payload=Objects.requireNonNull(migration.migrate(payload)); version=migration.toVersion();
            }
            final var changes=payload.path("changes"); if(!changes.isArray() || changes.size()>10000) throw new IOException("Invalid project changes list");
            final var result=new ArrayList<StoredRecipeChange>();
            for(final var node:changes) result.add(decode(node));
            final String directory=payload.path("modpackDirectory").asText();
            if(directory.isBlank() || !Path.of(directory).isAbsolute()) throw new IOException("Project must reference an absolute modpack directory");
            return new ProjectDocument(1,Path.of(directory),result);
        } catch(RuntimeException error) { throw new IOException("Invalid project: "+error.getMessage(),error); }
    }
    private StoredRecipeChange decode(final JsonNode node) {
        final var originals=new ArrayList<OriginalRecipeReference>();
        for(final var base:node.path("originals")) {
            final var source=decodeSource(base);
            final String hash=base.path("hash").asText(); if(!hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid base hash");
            originals.add(new OriginalRecipeReference(source,hash));
        }
        final var raw=new ArrayList<String>(); node.path("replacementJson").forEach(value->{ if(!value.isTextual()) throw new IllegalArgumentException("Replacement must be JSON text"); raw.add(value.asText()); });
        final var metadata=new LinkedHashMap<String,String>(); node.path("metadata").fields().forEachRemaining(field->metadata.put(field.getKey(),field.getValue().asText()));
        final var sources=new ArrayList<RecipeSource>();
        if(node.has("replacementSources")) {
            if(!node.path("replacementSources").isArray())throw new IllegalArgumentException("Invalid replacement provenance");
            node.path("replacementSources").forEach(value->sources.add(decodeSource(value)));
        }
        return new StoredRecipeChange(ResourceLocation.parse(node.path("id").asText()),RecipeChangeType.valueOf(node.path("type").asText()),originals,raw,Instant.parse(node.path("timestamp").asText()),metadata,sources);
    }
    private RecipeSource decodeSource(final JsonNode node) {
        final var mods=new ArrayList<String>(); node.path("modIds").forEach(mod->mods.add(mod.asText()));
        return new RecipeSource(SourceType.valueOf(node.path("sourceType").asText()),mods,Path.of(node.path("archivePath").asText()),node.path("internalPath").asText(),node.path("method").asText());
    }
    public void write(final Path requested,final ProjectDocument project) throws IOException {
        Objects.requireNonNull(requested); Objects.requireNonNull(project);
        if(project.changes().size()>10000) throw new IOException("Too many recipe changes");
        final var parent=requested.toAbsolutePath().normalize().getParent().toRealPath();
        final var file=parent.resolve(requested.getFileName()); final var backup=parent.resolve(requested.getFileName()+".bak");
        final var sourceRoot=project.modpackDirectory().toRealPath();
        if(file.startsWith(sourceRoot) || Files.isSymbolicLink(file) || Files.isSymbolicLink(backup)) throw new IOException("Project must be saved outside the source modpack, without symbolic links");
        if(Files.exists(file) && !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Project destination is not a regular file");
        // Serialize and check before touching the previous working file.
        final var payload=mapper.createObjectNode(); payload.put("modpackDirectory",sourceRoot.toString());
        final var array=payload.putArray("changes"); project.changes().forEach(change->array.add(encode(change)));
        final var envelope=mapper.createObjectNode(); envelope.put("formatVersion",1); envelope.put("sha256",hash(payload)); envelope.set("project",payload);
        final var serialized=new ByteArrayOutputStream();
        try(final var limited=new OutputStream() {
            private long count;
            @Override public void write(final int value) throws IOException { if(++count>48L*1024*1024) throw new IOException("Project exceeds byte limit"); serialized.write(value); }
            @Override public void write(final byte[] data,final int offset,final int length) throws IOException { if((count+=length)>48L*1024*1024) throw new IOException("Project exceeds byte limit"); serialized.write(data,offset,length); }
        }) { mapper.writerWithDefaultPrettyPrinter().writeValue(limited,envelope); }
        final byte[] bytes=serialized.toByteArray();
        if(bytes.length>48L*1024*1024) throw new IOException("Project exceeds 48 MiB limit");
        // Validate raw JSON and change shape before creating any output file.
        final var reader=new ResourceJsonReader();
        final var ids=new HashSet<dev.recipeeditor.core.ResourceLocation>();
        for(final var change:project.changes()) {
            if(!ids.add(change.id())) throw new IOException("Duplicate change ID");
            if(change.type()==RecipeChangeType.DELETE?!change.replacementJson().isEmpty():change.replacementJson().size()!=1) throw new IOException("Invalid replacement count");
            if(change.type()==RecipeChangeType.CREATE?!change.originals().isEmpty():change.originals().isEmpty()) throw new IOException("Invalid original references");
            for(final var raw:change.replacementJson()) {
                if(raw.getBytes(StandardCharsets.UTF_8).length>2*1024*1024) throw new IOException("Recipe JSON exceeds limit");
                final var json=reader.read(raw);
                if(json==null || !json.isObject() || !json.path("type").isTextual()) throw new IOException("Invalid replacement JSON");
                dev.recipeeditor.core.ResourceLocation.parse(json.path("type").asText());
            }
        }
        final var temporary=Files.createTempFile(parent,".recipe-project-",".tmp");
        Path backupTemporary=null;
        try {
            try(final var channel=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                final var buffer=ByteBuffer.wrap(bytes); while(buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            if(Files.exists(file)) {
                read(file); // Never replace a corrupted project silently; recovery is explicit via .bak.
                backupTemporary=Files.createTempFile(parent,".recipe-backup-",".tmp"); Files.copy(file,backupTemporary,StandardCopyOption.REPLACE_EXISTING); replace(backupTemporary,backup); backupTemporary=null;
            }
            replace(temporary,file);
        } finally { Files.deleteIfExists(temporary); if(backupTemporary!=null) Files.deleteIfExists(backupTemporary); }
    }
    private ObjectNode encode(final StoredRecipeChange change) {
        final var node=mapper.createObjectNode(); node.put("id",change.id().toString()); node.put("type",change.type().name()); node.put("timestamp",change.timestamp().toString());
        final var originals=node.putArray("originals");
        for(final var base:change.originals()) {
            final var value=originals.addObject(); value.put("hash",base.contentHash());encodeSource(value,base.source());
        }
        final var sources=node.putArray("replacementSources"); change.replacementSources().forEach(source->encodeSource(sources.addObject(),source));
        final var raw=node.putArray("replacementJson"); change.replacementJson().forEach(raw::add);
        final var metadata=node.putObject("metadata"); change.metadata().forEach(metadata::put); return node;
    }
    private void encodeSource(final ObjectNode node,final RecipeSource source) {
        node.put("sourceType",source.type().name()); final var mods=node.putArray("modIds");source.modIds().forEach(mods::add);
        node.put("archivePath",source.archivePath().toString());node.put("internalPath",source.internalPath());node.put("method",source.discoveryMethod());
    }
    private String hash(final JsonNode value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private void replace(final Path from,final Path to) throws IOException {
        try { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException error) { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override public void close() { worker.shutdownNow(); }
}
