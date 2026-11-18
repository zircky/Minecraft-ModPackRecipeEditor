package dev.recipeeditor.exporters;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Profiles and successful manifests live beside the saved project, not inside the source modpack. */
public final class ExportMetadataStore {
    private final ObjectMapper mapper=new ObjectMapper();
    public Path path(final Path project){return project.toAbsolutePath().resolveSibling(project.getFileName()+".exports.json");}
    public ExportWorkspace load(final Path project)throws IOException {
        final var file=path(project);if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return new ExportWorkspace();
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>16L*1024*1024)throw new IOException("Invalid export metadata file");
        try(final var input=Files.newInputStream(file)) {
            final var root=new ResourceJsonReader().read(new String(SafeFiles.read(input,16*1024*1024),java.nio.charset.StandardCharsets.UTF_8));
            if(root.path("version").asInt(-1)!=1)throw new IOException("Unsupported export metadata version");
            final var payload=root.path("workspace");if(!root.path("sha256").asText().equals(ExportText.hash(payload.toString())))throw new IOException("Export metadata integrity failure");
            if(!payload.path("profiles").isArray()||!payload.path("manifests").isArray()||payload.path("profiles").size()>128||payload.path("manifests").size()>128)throw new IOException("Export metadata limits exceeded");
            final var profiles=new ArrayList<ExportProfile>();final var manifests=new ArrayList<ExportManifest>();
            for(final var node:payload.path("profiles"))profiles.add(new ExportProfile(node.path("id").asText(),node.path("name").asText(),Path.of(node.path("target").asText()),ExportFormat.valueOf(node.path("format").asText()),OverwritePolicy.valueOf(node.path("overwrite").asText()),node.path("backups").asBoolean(),node.path("minecraft").asText(),node.path("loader").asText()));
            for(final var node:payload.path("manifests")) {
                final var hashes=new TreeMap<String,String>();node.path("hashes").fields().forEachRemaining(e->{ExportPaths.managed(node.path("profile").asText(),e.getKey());if(!e.getValue().asText().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid manifest hash");hashes.put(e.getKey(),e.getValue().asText());});
                final var formats=new HashSet<ExportFormat>();node.path("formats").forEach(value->formats.add(ExportFormat.valueOf(value.asText())));final var ids=new ArrayList<String>();node.path("recipeIds").forEach(value->{dev.recipeeditor.core.ResourceLocation.parse(value.asText());ids.add(value.asText());});
                if(hashes.size()>10000||ids.size()>10000)throw new IOException("Manifest limits exceeded");
                manifests.add(new ExportManifest(node.path("profile").asText(),Path.of(node.path("target").asText()),formats,node.path("minecraft").asText(),Instant.parse(node.path("timestamp").asText()),hashes,ids,node.path("status").asText()));
            }
            return new ExportWorkspace(profiles,manifests);
        }catch(RuntimeException error){throw new IOException("Invalid export metadata: "+error.getMessage(),error);}
    }
    public void saveProfile(final Path project,final ExportProfile profile)throws IOException {update(project,profile,null);}
    public void commit(final Path project,final ExportProfile profile,final ExportManifest manifest)throws IOException {update(project,profile,manifest);}
    private void update(final Path project,final ExportProfile profile,final ExportManifest manifest)throws IOException {
        final var file=path(project);final var parent=file.getParent().toRealPath();
        if(!parent.equals(file.getParent().toAbsolutePath().normalize()))throw new IOException("Unexpected export metadata parent redirection");
        final var lock=ExportPaths.inside(parent,file.getFileName()+".lock");
        try(final var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);final var held=channel.tryLock()) {
            if(held==null)throw new IOException("Another export holds the project metadata lock");
            save(project,updated(load(project),profile,manifest));
        }
    }
    private ExportWorkspace updated(final ExportWorkspace old,final ExportProfile profile,final ExportManifest manifest) {
        final var profiles=new ArrayList<>(old.profiles().stream().filter(p->!p.id().equals(profile.id())).toList());profiles.add(profile);
        final var manifests=new ArrayList<>(old.manifests());if(manifest!=null){manifests.removeIf(m->m.profileId().equals(manifest.profileId())&&m.targetDirectory().equals(manifest.targetDirectory()));manifests.add(manifest);}return new ExportWorkspace(profiles,manifests);
    }
    private void save(final Path project,final ExportWorkspace workspace)throws IOException {
        if(workspace.profiles().size()>128||workspace.manifests().size()>128)throw new IOException("Export workspace limit reached");
        final var file=path(project);final var parent=file.getParent().toRealPath();if(Files.isSymbolicLink(file))throw new IOException("Metadata symlink rejected");
        final var payload=mapper.createObjectNode();final var profiles=payload.putArray("profiles");
        for(final var profile:workspace.profiles()){final var n=profiles.addObject();n.put("id",profile.id());n.put("name",profile.name());n.put("target",profile.targetDirectory().toString());n.put("format",profile.preferredFormat().name());n.put("overwrite",profile.overwritePolicy().name());n.put("backups",profile.backups());n.put("minecraft",profile.minecraftVersion());n.put("loader",profile.loader());}
        final var manifests=payload.putArray("manifests");
        for(final var manifest:workspace.manifests()){final var n=manifests.addObject();n.put("profile",manifest.profileId());n.put("target",manifest.targetDirectory().toString());n.put("minecraft",manifest.minecraftVersion());n.put("timestamp",manifest.timestamp().toString());n.put("status",manifest.status());final var formats=n.putArray("formats");manifest.formats().stream().sorted().forEach(f->formats.add(f.name()));final var hashes=n.putObject("hashes");new TreeMap<>(manifest.fileHashes()).forEach(hashes::put);final var ids=n.putArray("recipeIds");manifest.recipeIds().forEach(ids::add);}
        final var root=mapper.createObjectNode();root.put("version",1);root.put("sha256",ExportText.hash(payload.toString()));root.set("workspace",payload);
        final byte[] bytes=mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);if(bytes.length>16L*1024*1024)throw new IOException("Export metadata exceeds byte limit");
        final var temp=Files.createTempFile(parent,".export-metadata-",".tmp");
        try{Files.write(temp,bytes);try(final var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){final var backup=file.resolveSibling(file.getFileName()+".bak");if(Files.isSymbolicLink(backup))throw new IOException("Metadata backup symlink rejected");if(Files.exists(file))Files.copy(file,backup,StandardCopyOption.REPLACE_EXISTING);try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}catch(IOException failed){if(Files.exists(backup))Files.copy(backup,file,StandardCopyOption.REPLACE_EXISTING);throw failed;}}}finally{try{Files.deleteIfExists(temp);}catch(IOException cleanup){org.slf4j.LoggerFactory.getLogger(ExportMetadataStore.class).warn("Metadata temporary cleanup failed: {}",temp,cleanup);}}
    }
}
