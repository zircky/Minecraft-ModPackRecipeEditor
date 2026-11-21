package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionprotocol.RuntimeData.*;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Fixed-entry snapshot bundle, bounded JSON records, checksum and replace-on-success. */
public final class RuntimeSnapshotService {
    private final long maxBytes=256L*1024*1024;
    public void save(final Path destination,final Snapshot snapshot)throws IOException {
        if(!snapshot.complete())throw new IOException("Partial snapshots cannot replace a complete snapshot");
        final var parent=destination.toAbsolutePath().normalize().getParent();for(Path existing=parent;existing!=null;existing=existing.getParent())if(Files.exists(existing,LinkOption.NOFOLLOW_LINKS))checkPath(existing);Files.createDirectories(parent);checkPath(parent);checkPathIfPresent(destination);
        if(Files.exists(destination))load(destination);
        final var temp=Files.createTempFile(parent,".mre-snapshot-",".tmp");
        try {
            try(final var zip=new ZipOutputStream(Files.newOutputStream(temp))) {
                final var manifest=new com.google.gson.JsonObject();manifest.addProperty("schemaVersion",snapshot.schemaVersion());manifest.addProperty("protocolVersion",1);manifest.addProperty("snapshotId",snapshot.snapshotId());manifest.addProperty("complete",true);manifest.add("environment",ProtocolCodec.GSON.toJsonTree(snapshot.environment()));manifest.add("diagnostics",ProtocolCodec.GSON.toJsonTree(snapshot.diagnostics()));final var hashes=new com.google.gson.JsonObject();long total=0;
                final var datasets=new LinkedHashMap<String,List<?>>();datasets.put("registries",snapshot.registries());datasets.put("tags",snapshot.tags());datasets.put("recipes",snapshot.recipes());datasets.put("mods",snapshot.mods());if(snapshot.schemaVersion()==2)datasets.put("materials",snapshot.materials());
                for(final var entry:datasets.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()+".jsonl"));final var digest=digest();
                    for(final var row:entry.getValue()){final byte[] bytes=(ProtocolCodec.GSON.toJson(row)+"\n").getBytes(StandardCharsets.UTF_8);if(bytes.length>ProtocolCodec.MAX_RECORD_BYTES||(total+=bytes.length)>maxBytes)throw new IOException("Snapshot byte limit");zip.write(bytes);digest.update(bytes);}zip.closeEntry();hashes.addProperty(entry.getKey(),HexFormat.of().formatHex(digest.digest()));
                }
                manifest.add("hashes",hashes);manifest.addProperty("dataHash",HexFormat.of().formatHex(digest().digest(manifest.toString().getBytes(StandardCharsets.UTF_8))));
                zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(ProtocolCodec.GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            try(final var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}load(temp);
            final var backup=destination.resolveSibling(destination.getFileName()+".bak");checkPathIfPresent(backup);if(Files.exists(destination))Files.copy(destination,backup,StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException noAtomic){try{Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);}catch(IOException failure){if(Files.exists(backup))Files.copy(backup,destination,StandardCopyOption.REPLACE_EXISTING);throw failure;}}
        }finally{Files.deleteIfExists(temp);}
    }
    public Snapshot load(final Path path)throws IOException {
        checkPath(path);if(!Files.isRegularFile(path)||Files.size(path)>maxBytes)throw new IOException("Invalid snapshot file");
        try(final var zip=new ZipFile(path.toFile())) {
            if(zip.size()!=5&&zip.size()!=6)throw new IOException("Unexpected snapshot entries");final var names=new HashSet<String>();zip.stream().forEach(e->names.add(e.getName()));if(names.size()!=zip.size()||!names.contains("manifest.json"))throw new IOException("Duplicate/missing snapshot entries");
            final long[] total={0};final var manifest=ProtocolCodec.parse(read(zip,zip.getEntry("manifest.json"),ProtocolCodec.MAX_FRAME_BYTES,total)).getAsJsonObject();
            final int schema=manifest.get("schemaVersion").getAsInt();
            if(!manifest.get("protocolVersion").getAsString().equals("1")||(schema!=1&&schema!=2)||!manifest.get("complete").getAsBoolean())throw new IOException("Incomplete/unsupported snapshot");
            final var expectedNames=new HashSet<>(Set.of("manifest.json","registries.jsonl","tags.jsonl","recipes.jsonl","mods.jsonl"));if(schema==2)expectedNames.add("materials.jsonl");if(!names.equals(expectedNames))throw new IOException("Invalid snapshot entry names");
            final var hashes=manifest.getAsJsonObject("hashes");final String expectedHash=manifest.remove("dataHash").getAsString();if(!expectedHash.equals(HexFormat.of().formatHex(digest().digest(manifest.toString().getBytes(StandardCharsets.UTF_8)))))throw new IOException("Snapshot manifest checksum mismatch");
            return new Snapshot(schema,ProtocolCodec.required(manifest,"snapshotId"),true,ProtocolCodec.GSON.fromJson(manifest.get("environment"),Environment.class),rows(zip,"registries",Registry.class,hashes,total),rows(zip,"tags",Tag.class,hashes,total),rows(zip,"recipes",Recipe.class,hashes,total),rows(zip,"mods",Mod.class,hashes,total),strings(manifest.getAsJsonArray("diagnostics")),schema==2?rows(zip,"materials",Material.class,hashes,total):List.of());
        }catch(RuntimeException bad){throw new IOException("Invalid snapshot data",bad);}
    }
    private <T> List<T> rows(final ZipFile zip,final String name,final Class<T> type,final com.google.gson.JsonObject hashes,final long[] total)throws IOException {
        final var result=new ArrayList<T>();final var digest=digest();
        try(final var in=new BufferedInputStream(zip.getInputStream(zip.getEntry(name+".jsonl")))) {final var line=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(++total[0]>maxBytes)throw new IOException("Snapshot expansion limit");digest.update((byte)c);if(c=='\n'){if(result.size()>=200000)throw new IOException("Snapshot record limit");result.add(Objects.requireNonNull(ProtocolCodec.GSON.fromJson(ProtocolCodec.parse(line.toByteArray()),type)));line.reset();}else{if(line.size()>=ProtocolCodec.MAX_RECORD_BYTES)throw new IOException("Snapshot record byte limit");line.write(c);}}if(line.size()!=0)throw new IOException("Truncated snapshot record");}
        if(!HexFormat.of().formatHex(digest.digest()).equals(ProtocolCodec.required(hashes,name)))throw new IOException("Snapshot data checksum mismatch: "+name);return result;
    }
    private byte[] read(final ZipFile zip,final ZipEntry entry,final int max,final long[] total)throws IOException {try(final var in=zip.getInputStream(entry)){final var bytes=in.readNBytes(max+1);total[0]+=bytes.length;if(bytes.length>max)throw new IOException("Manifest byte limit");return bytes;}}
    private List<String> strings(final com.google.gson.JsonArray array){final var values=new ArrayList<String>();array.forEach(value->values.add(value.getAsString()));return values;}
    private void checkPath(final Path path)throws IOException {for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Snapshot symbolic link rejected");if(!path.toRealPath().equals(path.toAbsolutePath().normalize()))throw new IOException("Snapshot path redirection rejected");}
    private void checkPathIfPresent(final Path path)throws IOException {if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))checkPath(path);}
    private MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
