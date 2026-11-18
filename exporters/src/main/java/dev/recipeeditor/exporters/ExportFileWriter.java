package dev.recipeeditor.exporters;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.function.Consumer;

/** Stages every file first; validates hashes twice; rolls back output if application/manifest commit fails. */
public final class ExportFileWriter {
    @FunctionalInterface public interface Commit {void commit()throws IOException;}
    @FunctionalInterface public interface ApplyHook {void beforeFile(int index,Path stagedFile)throws IOException;}
    private final ApplyHook hook;
    public ExportFileWriter(){this((index,path)->{});}
    public ExportFileWriter(final ApplyHook hook){this.hook=Objects.requireNonNull(hook);}
    public void write(final ExportPlan plan,final ExportControl control,final Consumer<ExportProgress> progress,final Commit commit)throws IOException {
        if(!plan.valid())throw new IOException("Export preview has errors");
        final var root=plan.request().context().profile().targetDirectory().toRealPath();
        if(root.startsWith(plan.request().context().modpack().selectedDirectory().toRealPath())&&!plan.request().context().allowSourceDirectory())throw new IOException("Source-directory consent required");
        plan.files().keySet().forEach(path->ExportPaths.managed(plan.request().context().profile().id(),path));plan.obsoleteFiles().forEach(path->ExportPaths.managed(plan.request().context().profile().id(),path));
        final var lock=ExportPaths.inside(root,".recipe-editor-export.lock");
        try(final var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);final var held=channel.tryLock()) {
            if(held==null)throw new IOException("Another export holds the target lock");
            transact(root,plan,control,progress,commit);
        }
    }
    private void transact(final Path root,final ExportPlan plan,final ExportControl control,final Consumer<ExportProgress> progress,final Commit commit)throws IOException {
        final var stage=Files.createTempDirectory(root,".recipe-editor-stage-");final var staged=new LinkedHashMap<String,Path>();
        final var applied=new ArrayList<String>();final var backupFiles=new HashMap<String,Path>();boolean success=false,recovery=false;
        final var touched=new TreeSet<>(plan.files().keySet());touched.addAll(plan.obsoleteFiles());
        touched.removeIf(path->plan.files().containsKey(path)&&ExportText.hash(plan.files().get(path)).equals(plan.expectedHashes().get(path)));
        try {
            for(final var entry:plan.files().entrySet()) {
                control.check();final var file=stage.resolve("new").resolve(entry.getKey());Files.createDirectories(file.getParent());Files.writeString(file,entry.getValue(),java.nio.charset.StandardCharsets.UTF_8);
                try(final var out=FileChannel.open(file,StandardOpenOption.WRITE)){out.force(true);}
                staged.put(entry.getKey(),file);
            }
            for(final var path:touched){control.check();verifyCurrent(root,path,plan.expectedHashes().get(path));}
            int i=0;
            for(final var entry:staged.entrySet()){hook.beforeFile(i++,entry.getValue());if(!ExportPaths.hash(entry.getValue()).equals(ExportText.hash(plan.files().get(entry.getKey()))))throw new IOException("Staged file integrity failure: "+entry.getKey());}
            control.check();control.beginApply();progress.accept(new ExportProgress("Applying files - cancellation is now disabled",0.6,true));
            // Recovery journal survives process termination; all paths are relative to the selected target.
            final var journal=new StringBuilder("Recipe Editor transaction. Target: "+root+"\nExpected old hashes:\n");plan.expectedHashes().forEach((path,hash)->journal.append(path).append(" = ").append(hash).append("\n"));Files.writeString(stage.resolve("journal.txt"),journal);
            i=0;
            for(final var path:touched) {
                final var destination=ExportPaths.inside(root,path);verifyCurrent(root,path,plan.expectedHashes().get(path));
                if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) {final var backup=stage.resolve("old").resolve(path);Files.createDirectories(backup.getParent());Files.copy(destination,backup);backupFiles.put(path,backup);}
                hook.beforeFile(i++,staged.getOrDefault(path,stage));
                // Verify again immediately before rename/removal; never trust a changed stage or output.
                verifyCurrent(root,path,plan.expectedHashes().get(path));
                applied.add(path);
                if(plan.files().containsKey(path)) {
                    if(!ExportPaths.hash(staged.get(path)).equals(ExportText.hash(plan.files().get(path))))throw new IOException("Staged data changed before apply");
                    Files.createDirectories(destination.getParent());ExportPaths.inside(root,path);replace(staged.get(path),destination);
                }else Files.delete(destination);
                progress.accept(new ExportProgress("Applied "+applied.size()+" / "+touched.size(),0.6+0.3*applied.size()/Math.max(1,touched.size()),true));
            }
            if(!backupFiles.isEmpty()) {
                final String backupRoot=".recipe-editor-backups/"+UUID.randomUUID();
                for(final var entry:backupFiles.entrySet()){final var retained=ExportPaths.inside(root,backupRoot+"/"+entry.getKey());Files.createDirectories(retained.getParent());Files.copy(entry.getValue(),retained);}
            }
            commit.commit();success=true;
        }catch(Exception failure){
            Collections.reverse(applied);
            for(final var path:applied)try {
                final var file=ExportPaths.inside(root,path);final String expectedNew=plan.files().containsKey(path)?ExportText.hash(plan.files().get(path)):"";
                final String actual=Files.exists(file,LinkOption.NOFOLLOW_LINKS)?ExportPaths.hash(file):"";
                if(actual.equals(plan.expectedHashes().get(path)))continue;
                if(!actual.equals(expectedNew))throw new IOException("Concurrent modification prevents rollback: "+path);
                if(backupFiles.containsKey(path)){Files.createDirectories(file.getParent());replace(backupFiles.get(path),file);}else Files.deleteIfExists(file);
            }catch(IOException rollback){failure.addSuppressed(rollback);recovery=true;}
            if(recovery)throw new RecoveryRequiredException("Partial rollback. Recovery journal/backups retained at "+stage,failure);
            if(failure instanceof java.util.concurrent.CancellationException cancelled)throw cancelled;
            if(failure instanceof IOException io)throw io;throw new IOException("Export failed and was rolled back",failure);
        }finally{if(!recovery)try{cleanup(stage);}catch(IOException cleanup){org.slf4j.LoggerFactory.getLogger(ExportFileWriter.class).warn("Transaction stage cleanup failed: {} (applied={})",stage,success,cleanup);}}
    }
    private void verifyCurrent(final Path root,final String path,final String expected)throws IOException {
        if(expected==null)throw new IOException("Preview did not validate output path "+path);
        final var file=ExportPaths.inside(root,path);final String actual=Files.exists(file,LinkOption.NOFOLLOW_LINKS)?ExportPaths.hash(file):"";
        if(!actual.equals(expected))throw new IOException("File changed since preview: "+path);
    }
    private void replace(final Path from,final Path to)throws IOException {try{Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
    private void cleanup(final Path directory)throws IOException {
        // Only this freshly-created transaction directory, with no FOLLOW_LINKS, is cleaned.
        try(final var stream=Files.walk(directory)){for(final var file:stream.sorted(Comparator.reverseOrder()).toList()){if(!file.toAbsolutePath().normalize().startsWith(directory.toAbsolutePath().normalize()))throw new IOException("Unsafe stage cleanup path");Files.deleteIfExists(file);}}
    }
    public static final class RecoveryRequiredException extends IOException {public RecoveryRequiredException(final String message,final Throwable cause){super(message,cause);}}
}
