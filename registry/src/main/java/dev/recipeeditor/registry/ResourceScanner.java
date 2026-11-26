package dev.recipeeditor.registry;

import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.core.importing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** A bounded, non-extracting scan of imported JARs and explicit local datapack locations. */
public final class ResourceScanner {
    private final DiscoveryLimits limits;
    private final ResourceLayout layout;
    public ResourceScanner(final DiscoveryLimits limits, final ResourceLayout layout) { this.limits=limits; this.layout=layout; }
    public ResourceScanResult scan(final ModpackDefinition pack, final Consumer<ImportProgress> progress) {
        final var state=new State();
        final var vanilla=new VanillaAssetLocator().find(pack);state.diagnostics.addAll(vanilla.diagnostics());
        for(final var archive:vanilla.archives())scanArchive(archive.boundary(),archive.file(),SourceType.VANILLA_ASSET,List.of(),state);
        for(final var language:vanilla.languages())if(state.reserve(language.source()))try {
            state.documents.add(new ResourceDocument(language.source(),state.read(new ByteArrayInputStream(language.text().getBytes(StandardCharsets.UTF_8)))));
        }catch(IOException invalid){state.warn(language.source().container().toString(),invalid.getMessage());}
        int processed=0;
        final var seen=new HashSet<Path>();
        for (final var jar:pack.jars()) {
            SafeFiles.checkCancelled();
            if (!seen.add(jar.sourceJar().toAbsolutePath().normalize())) continue;
            scanArchive(pack.selectedDirectory(),jar.sourceJar(),SourceType.MOD_JAR,
                    jar.mods().stream().map(m->m.metadata().id()).filter(id->!id.isBlank()).distinct().toList(),state);
            progress.accept(new ImportProgress("Scanning resources...",++processed,pack.jars().size(),0.05+0.45*processed/Math.max(1,pack.jars().size())));
            if (state.stopped) break;
        }
        if (!state.stopped) scanLocal(pack,state);
        return new ResourceScanResult(state.documents,state.textures,state.diagnostics);
    }
    private void scanArchive(final Path boundary,final Path archive,final SourceType type,final List<String> mods,final State state) {
        try {
            final var safe=SafeFiles.inside(boundary,archive);
            if (!Files.isRegularFile(safe) || Files.size(safe)>limits.maxArchiveBytes()) throw new IOException("Archive exceeds size limit or is not a file");
            try (final var zip=new ZipFile(safe.toFile())) {
                if (zip.size()>limits.maxArchiveEntries()) throw new IOException("Archive entry limit exceeded");
                final var entries=zip.entries();
                while (entries.hasMoreElements() && !state.stopped) {
                    SafeFiles.checkCancelled();
                    final var entry=entries.nextElement();
                    final String name=entry.getName();
                    if (entry.isDirectory()) continue;
                    if (!validPath(name)) { state.warn(archive+"!"+name,"Unsafe resource path ignored"); continue; }
                    if (!relevant(name)) continue;
                    if(type==SourceType.VANILLA_ASSET&&!name.startsWith("assets/minecraft/")
                            && !(layout.usesDatapacks() && name.startsWith("data/minecraft/tags/"))
                            && !(layout.includeVanillaRecipes() && name.startsWith("data/minecraft/") && layout.isRecipe(name)))continue;
                    final var source=new ResourceSource(type,mods,archive,name,type==SourceType.VANILLA_ASSET?"Vanilla client assets; available preview, active resource order UNKNOWN":"Static resource scan");
                    if (!state.reserve(source)) break;
                    if (name.endsWith(".png")) { state.textures.add(source); continue; }
                    try {
                        if (entry.getSize()>limits.maxJsonBytes()) throw new IOException("JSON size limit exceeded");
                        if (entry.getSize()>65536 && entry.getCompressedSize()>0 && entry.getSize()/entry.getCompressedSize()>200)
                            throw new IOException("Suspicious compression ratio");
                        try (final var input=zip.getInputStream(entry)) { state.documents.add(new ResourceDocument(source,state.read(input))); }
                    } catch (IOException error) { state.warn(archive+"!"+name,error.getMessage()); }
                }
            }
        } catch (IOException | IllegalArgumentException error) { state.warn(archive.toString(),"Cannot scan archive: "+error.getMessage()); }
    }
    private void scanLocal(final ModpackDefinition pack,final State state) {
        final var game=pack.gameDirectory();
        scanPackCollection(pack.selectedDirectory(),game.resolve("resourcepacks"),state);
        if (!layout.usesDatapacks()) return;
        scanPackCollection(pack.selectedDirectory(),game.resolve("datapacks"),state);
        scanPackCollection(pack.selectedDirectory(),game.resolve("config/openloader/data"),state);
        scanTree(pack.selectedDirectory(),game.resolve("kubejs"),SourceType.LOCAL_RESOURCE,state);
        // Only immediate worlds and their datapacks; never traverse saves or the entire instance recursively.
        final var saves=game.resolve("saves");
        if (!Files.isDirectory(saves)) return;
        try {
            final var safe=SafeFiles.inside(pack.selectedDirectory(),saves);
            try (final var worlds=Files.newDirectoryStream(safe)) {
                int count=0;
                for (final var world:worlds) {
                    if (++count>64) { state.warn(saves.toString(),"World limit reached (64); remaining worlds not scanned"); break; }
                    SafeFiles.checkCancelled();
                    SafeFiles.inside(pack.selectedDirectory(),world);
                    if (Files.isDirectory(world)) scanPackCollection(pack.selectedDirectory(),world.resolve("datapacks"),state);
                }
            }
        } catch (IOException error) { state.warn(saves.toString(),error.getMessage()); }
    }
    private void scanPackCollection(final Path boundary,final Path directory,final State state) {
        if (!Files.isDirectory(directory) || state.stopped) return;
        try {
            final var safe=SafeFiles.inside(boundary,directory);
            try (final var stream=Files.newDirectoryStream(safe)) {
                int count=0;
                for (final var path:stream) {
                    SafeFiles.checkCancelled();
                    if (++count>256) { state.warn(directory.toString(),"Datapack limit reached (256)"); break; }
                    final var real=SafeFiles.inside(boundary,path);
                    if (Files.isDirectory(real)) scanTree(boundary,real,SourceType.DATAPACK,state);
                    else if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) scanArchive(boundary,path,SourceType.DATAPACK,List.of(),state);
                }
            }
        } catch (IOException error) { state.warn(directory.toString(),error.getMessage()); }
    }
    private void scanTree(final Path boundary,final Path directory,final SourceType type,final State state) {
        if (!Files.isDirectory(directory) || state.stopped) return;
        try {
            final var safe=SafeFiles.inside(boundary,directory);
            try (final var paths=Files.walk(safe,24)) {
                final var iterator=paths.iterator();
                int visited=0;
                while (iterator.hasNext() && !state.stopped) {
                    SafeFiles.checkCancelled();
                    final var path=iterator.next();
                    if (++visited>200000) { state.warn(directory.toString(),"Local traversal limit exceeded"); break; }
                    if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) continue;
                    try {
                        SafeFiles.inside(boundary,path);
                        final String name=safe.relativize(path).toString().replace('\\','/');
                        if (!validPath(name) || !relevant(name)) continue;
                        final var source=new ResourceSource(type,List.of(),safe,name,"Local static resource scan");
                        if (!state.reserve(source)) break;
                        if (name.endsWith(".png")) state.textures.add(source);
                        else {
                            if (Files.size(path)>limits.maxJsonBytes()) throw new IOException("JSON size limit exceeded");
                            try (final var input=Files.newInputStream(path)) { state.documents.add(new ResourceDocument(source,state.read(input))); }
                        }
                    } catch (IOException error) { state.warn(path.toString(),error.getMessage()); }
                }
            }
        } catch (IOException | UncheckedIOException error) { state.warn(directory.toString(),error.getMessage()); }
    }
    public boolean validPath(final String path) {
        return !path.startsWith("/") && !path.contains("\\") && !path.contains(":")
                && Arrays.stream(path.split("/",-1)).noneMatch(s->s.isEmpty() || s.equals(".") || s.equals(".."));
    }
    private boolean relevant(final String path) {
        return path.matches("assets/[a-z0-9_.-]+/(lang|models|blockstates|atlases)/[a-z0-9/._-]+\\.json")
                || path.matches("assets/[a-z0-9_.-]+/textures/[a-z0-9/._-]+\\.png(\\.mcmeta)?")
                || layout.isRecipe(path)
                || (layout.usesDatapacks() && path.matches("data/[a-z0-9_.-]+/tags/[a-z0-9/._-]+\\.json"));
    }
    private final class State {
        private final List<ResourceDocument> documents=new ArrayList<>();
        private final List<ResourceSource> textures=new ArrayList<>();
        private final List<ImportDiagnostic> diagnostics=new ArrayList<>();
        private long bytes;
        private int count;
        private boolean stopped;
        boolean reserve(final ResourceSource source) {
            if (++count>limits.maxDocuments()) { stopped=true; warn(source.container().toString(),"Resource count limit reached; index is incomplete"); }
            return !stopped;
        }
        String read(final InputStream input) throws IOException {
            final var output=new ByteArrayOutputStream(); final var buffer=new byte[8192];
            int count;
            while ((count=input.read(buffer))!=-1) {
                SafeFiles.checkCancelled(); bytes+=count;
                if (bytes>limits.maxTotalBytes()) { stopped=true; throw new IOException("Aggregate JSON byte limit reached; index is incomplete"); }
                if (output.size()+count>limits.maxJsonBytes()) throw new IOException("JSON byte limit reached");
                output.write(buffer,0,count);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
        void warn(final String source,final String text) {
            if (diagnostics.size()<10000) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,source,text));
        }
    }
}
