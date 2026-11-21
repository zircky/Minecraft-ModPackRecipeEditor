package dev.recipeeditor.registry;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.core.importing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline, version-scoped lookup of exact client artifacts; never searches a disk or downloads assets. */
public final class VanillaAssetLocator {
    public record Archive(Path boundary,Path file) { }
    public record Result(List<Archive> archives,List<ResourceDocument> languages,List<ImportDiagnostic> diagnostics) {
        public Result { archives=List.copyOf(archives);languages=List.copyOf(languages);diagnostics=List.copyOf(diagnostics); }
    }
    private final List<Path> sharedRoots;
    public VanillaAssetLocator() {
        final String appData=System.getenv("APPDATA");
        sharedRoots=appData==null?List.of():List.of(Path.of(appData).resolve("PrismLauncher"));
    }
    VanillaAssetLocator(final List<Path> sharedRoots){this.sharedRoots=List.copyOf(sharedRoots);}

    public Result find(final ModpackDefinition pack){return find(pack.selectedDirectory(),pack.gameDirectory(),pack.minecraftVersion().value());}
    public Set<Path> approvedArchives(final Path selected) throws IOException {
        final var game=Files.isDirectory(selected.resolve("minecraft"))?selected.resolve("minecraft"):Files.isDirectory(selected.resolve(".minecraft"))?selected.resolve(".minecraft"):selected;
        // The pipeline is explicitly limited to 1.20.1; do not accept another instance version.
        if(Files.isRegularFile(selected.resolve("mmc-pack.json"))) {
            final var components=json(selected,selected.resolve("mmc-pack.json"),2*1024*1024).path("components");
            boolean matching=false;
            for(final var component:components)if(component.path("uid").asText().equals("net.minecraft"))matching=component.path("version").asText().equals("1.20.1");
            if(!matching)return Set.of();
        }
        final var result=new HashSet<Path>();
        for(final var archive:find(selected,game,"1.20.1").archives())result.add(archive.file().toRealPath());
        return Set.copyOf(result);
    }
    private Result find(final Path selected,final Path game,final String version) {
        final var archives=new ArrayList<Archive>();final var languages=new ArrayList<ResourceDocument>();final var diagnostics=new ArrayList<ImportDiagnostic>();
        if(!version.equals("1.20.1"))return new Result(archives,languages,diagnostics);
        final var roots=new LinkedHashSet<Path>();
        if(Files.isRegularFile(selected.resolve("mmc-pack.json"))) {
            final var parent=selected.toAbsolutePath().normalize().getParent();
            if(parent!=null&&parent.getFileName().toString().equalsIgnoreCase("instances")&&parent.getParent()!=null)roots.add(parent.getParent());
            roots.addAll(sharedRoots);
        }
        final var hashes=new HashSet<String>();
        for(final var launcher:roots) {
            final var metadata=launcher.resolve("meta/net.minecraft/"+version+".json");
            if(!Files.isRegularFile(metadata))continue;
            try {
                final var manifest=json(launcher,metadata,2*1024*1024);
                if(!manifest.path("uid").asText().equals("net.minecraft")||!manifest.path("version").asText().equals(version))throw new IOException("Client metadata version/identity mismatch");
                if(!manifest.path("mainJar").path("name").asText().equals("com.mojang:minecraft:"+version+":client"))throw new IOException("Unrecognized client artifact coordinate");
                final var client=launcher.resolve("libraries/com/mojang/minecraft/"+version+"/minecraft-"+version+"-client.jar");
                if(Files.isRegularFile(client)) {
                    final var artifact=manifest.path("mainJar").path("downloads").path("artifact");
                    final String hash=verifiedHash(launcher,client,artifact,256*1024*1024);
                    if(hashes.add(hash))archives.add(new Archive(launcher.toRealPath(),client.toRealPath()));
                } else warn(diagnostics,client,"Vanilla client JAR is absent; no download attempted");
                languageObjects(launcher,manifest.path("assetIndex"),languages);
            } catch(IOException|IllegalArgumentException invalid){warn(diagnostics,metadata,invalid.getMessage());}
        }
        // Standard version directory layouts, bounded to the selected instance only.
        for(final var directory:new LinkedHashSet<>(List.of(game,selected))) {
            final var metadata=directory.resolve("versions/"+version+"/"+version+".json");
            if(!Files.isRegularFile(metadata))continue;
            try {
                final var manifest=json(selected,metadata,2*1024*1024);
                if(!manifest.path("id").asText().equals(version))throw new IOException("Version metadata mismatch");
                final var client=metadata.resolveSibling(version+".jar");
                if(Files.isRegularFile(client)) {
                    final String hash=verifiedHash(selected,client,manifest.path("downloads").path("client"),256*1024*1024);
                    if(hashes.add(hash))archives.add(new Archive(selected.toRealPath(),client.toRealPath()));
                }
                languageObjects(directory,manifest.path("assetIndex"),languages);
            }catch(IOException|IllegalArgumentException invalid){warn(diagnostics,metadata,invalid.getMessage());}
        }
        if(archives.isEmpty())warn(diagnostics,selected,"Vanilla assets unavailable: no verified local Minecraft 1.20.1 client JAR found in supported instance/Prism locations");
        return new Result(archives,languages,diagnostics);
    }
    private void languageObjects(final Path launcher,final JsonNode descriptor,final List<ResourceDocument> documents) throws IOException {
        final String id=descriptor.path("id").asText();
        if(!id.matches("[a-zA-Z0-9_.-]{1,64}"))throw new IOException("Missing/unsafe asset index ID");
        final var index=launcher.resolve("assets/indexes/"+id+".json");
        verifiedHash(launcher,index,descriptor,8*1024*1024);
        final var objects=json(launcher,index,8*1024*1024).path("objects");
        for(final String language:List.of("en_us","ru_ru")) {
            final var entry=objects.path("minecraft/lang/"+language+".json");
            if(entry.isMissingNode())continue;
            final String hash=entry.path("hash").asText();
            if(!hash.matches("[a-f0-9]{40}"))throw new IOException("Invalid language object hash");
            final var object=launcher.resolve("assets/objects/"+hash.substring(0,2)+"/"+hash);
            if(!Files.isRegularFile(object))continue;
            final var expected=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("sha1",hash).put("size",entry.path("size").asLong(-1));
            verifiedHash(launcher,object,expected,2*1024*1024);
            final var source=new ResourceSource(SourceType.VANILLA_ASSET,List.of(),object.toRealPath(),"assets/minecraft/lang/"+language+".json","Vanilla asset index "+id+"; object SHA-1 "+hash+"; available translation, active pack order UNKNOWN");
            final var document=new ResourceDocument(source,new String(SafeFiles.read(launcher,object,2*1024*1024),StandardCharsets.UTF_8));
            if(documents.stream().noneMatch(old->old.source().equals(source)))documents.add(document);
        }
    }
    private String verifiedHash(final Path boundary,final Path file,final JsonNode descriptor,final int limit) throws IOException {
        final String expected=descriptor.path("sha1").asText();
        final var safe=SafeFiles.inside(boundary,file);
        if(!expected.matches("[a-f0-9]{40}")||!Files.isRegularFile(safe)||Files.size(safe)>limit||Files.size(safe)!=descriptor.path("size").asLong(-1))throw new IOException("Invalid asset size/hash metadata: "+file);
        try {
            final var digest=MessageDigest.getInstance("SHA-1");
            try(final var input=Files.newInputStream(safe)){final byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1){SafeFiles.checkCancelled();digest.update(buffer,0,count);}}
            final String hash=HexFormat.of().formatHex(digest.digest());if(!hash.equals(expected))throw new IOException("Asset checksum mismatch: "+file);return hash;
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private JsonNode json(final Path boundary,final Path file,final int limit) throws IOException {return new ResourceJsonReader().read(new String(SafeFiles.read(boundary,file,limit),StandardCharsets.UTF_8));}
    private void warn(final List<ImportDiagnostic> diagnostics,final Path source,final String message){diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,source.toString(),message));}
}
