package dev.recipeeditor.exporters;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class RecipeExportService implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("recipe-export-",0).factory());
    private final ExportPlanner planner;private final ExportFileWriter writer;private final ExportMetadataStore metadata;
    public RecipeExportService(final ExportPlanner planner){this(planner,new ExportFileWriter(),new ExportMetadataStore());}
    public RecipeExportService(final ExportPlanner planner,final ExportFileWriter writer,final ExportMetadataStore metadata){this.planner=planner;this.writer=writer;this.metadata=metadata;}
    public CompletableFuture<ExportWorkspace> workspace(final Path project){return CompletableFuture.supplyAsync(()->{try{return metadata.load(project);}catch(java.io.IOException e){throw new CompletionException(e);}},worker);}
    public CompletableFuture<Void> saveProfile(final Path project,final ExportProfile profile){return CompletableFuture.runAsync(()->{try{metadata.saveProfile(project,profile);}catch(java.io.IOException e){throw new CompletionException(e);}},worker);}
    public CompletableFuture<ExportPlan> dryRun(final ExportRequest request){return CompletableFuture.supplyAsync(()->planner.plan(request),worker);}
    public ExportOperation export(final ExportPlan preview,final Consumer<ExportProgress> progress) {
        final var control=new ExportControl();
        final var future=CompletableFuture.supplyAsync(()-> {
            final var diagnostics=new ArrayList<ExportDiagnostic>();
            try {
                control.check();progress.accept(new ExportProgress("Revalidating preview and sources",0.05,false));
                final var checked=planner.plan(preview.request());
                if(!checked.valid()||!checked.files().equals(preview.files())||!checked.expectedHashes().equals(preview.expectedHashes())||!checked.obsoleteFiles().equals(preview.obsoleteFiles()))throw new java.io.IOException("Preview is stale or invalid; rebuild it before export");
                final var context=preview.request().context();final var hashes=new TreeMap<String,String>();preview.files().forEach((path,text)->hashes.put(path,ExportText.hash(text)));
                final var manifest=new ExportManifest(context.profile().id(),context.profile().targetDirectory().toRealPath(),preview.recipes().stream().map(PlannedRecipe::format).collect(java.util.stream.Collectors.toSet()),context.modpack().minecraftVersion().value(),java.time.Instant.now(),hashes,preview.recipes().stream().map(r->r.change().id().toString()).toList(),"GENERATED_RUNTIME_UNVERIFIED");
                writer.write(preview,control,progress,()->metadata.commit(context.projectFile(),context.profile(),manifest));
                diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.INFO,ExportDiagnostic.Code.EXPORT_SUCCESS,"Generated files and saved export manifest; loading in Minecraft has not been tested",List.of(),context.profile().preferredFormat(),"Validate in an isolated Minecraft instance"));
                return new ExportResult(ExportResult.Status.SUCCESS,diagnostics,List.of(manifest));
            }catch(Exception failure){final boolean cancelled=failure instanceof CancellationException;diagnostics.add(new ExportDiagnostic(cancelled?ExportDiagnostic.Level.INFO:ExportDiagnostic.Level.ERROR,cancelled?ExportDiagnostic.Code.EXPORT_CANCELLED:ExportDiagnostic.Code.EXPORT_WRITE_FAILED,failure.getMessage(),List.of(),preview.request().context().profile().preferredFormat(),"Review diagnostics; recovery data is retained if rollback could not finish"));return new ExportResult(cancelled?ExportResult.Status.CANCELLED:failure instanceof ExportFileWriter.RecoveryRequiredException?ExportResult.Status.RECOVERY_REQUIRED:ExportResult.Status.FAILED,diagnostics,List.of());}
        },worker);
        return new ExportOperation(){public CompletableFuture<ExportResult> result(){return future;}public boolean cancel(){return control.cancel();}public boolean applying(){return control.applying();}};
    }
    @Override public void close(){worker.shutdown();}
}
