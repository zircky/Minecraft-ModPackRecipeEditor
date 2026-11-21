package dev.recipeeditor.app;

import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.integrations.Minecraft1201Integration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application-level composition of the resource and recipe pipelines. */
public final class DefaultStaticAnalysisService implements StaticAnalysisService {
    private final Logger logger=LoggerFactory.getLogger(DefaultStaticAnalysisService.class);
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("resource-index-",0).factory());
    private final Set<DiscoveryOperation> active=ConcurrentHashMap.newKeySet();
    @Override public DiscoveryOperation start(final ModpackDefinition pack,final Consumer<ImportProgress> progress) {
        Objects.requireNonNull(pack); Objects.requireNonNull(progress);
        final var future=new CompletableFuture<DiscoveryResult>();
        final var task=new FutureTask<Void>(()-> {
            try { future.complete(analyze(pack,progress)); }
            catch (CancellationException error) { future.cancel(false); }
            catch (Exception error) { logger.warn("Indexing failed",error); future.completeExceptionally(error); }
            return null;
        });
        final var operation=new DiscoveryOperation() {
            @Override public CompletableFuture<DiscoveryResult> result() { return future; }
            @Override public void cancel() { task.cancel(true); future.cancel(false); }
        };
        active.add(operation); future.whenComplete((result,error)->active.remove(operation));
        try { worker.execute(task); } catch (RejectedExecutionException error) { future.completeExceptionally(error); }
        return operation;
    }
    private DiscoveryResult analyze(final ModpackDefinition pack,final Consumer<ImportProgress> progress) {
        final long start=System.nanoTime(); final var diagnostics=new ArrayList<ImportDiagnostic>();
        report(progress,"Indexing modpack...",0);
        final var integration=new Minecraft1201Integration();
        if (!integration.supports(pack.minecraftVersion().value())) {
            diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,"Version","Static indexing currently supports Minecraft 1.20.1 only; no version was guessed"));
            return new DiscoveryResult(pack.selectedDirectory(),new RegistryIndex(List.of()),new RecipeIndex(List.of(),new TagIndex(List.of())),diagnostics,0);
        }
        final var scan=new ResourceScanner(new DiscoveryLimits(),integration.layout()).scan(pack,progress);
        diagnostics.addAll(scan.diagnostics());
        final var localization=new LocalizationService(scan.documents()); diagnostics.addAll(localization.diagnostics());
        final var tags=new TagIndex(scan.documents()); diagnostics.addAll(tags.diagnostics());
        final var resources=new ArrayList<RegistryEntry>();
        report(progress,"Indexing items...",0.55);
        resources.addAll(new ItemResourceIndexer().index(scan,localization,tags,"en_us",diagnostics));
        report(progress,"Indexing blocks...",0.65);
        resources.addAll(new BlockResourceIndexer().index(scan,localization,tags,"en_us",diagnostics));
        report(progress,"Indexing fluids...",0.72);
        resources.addAll(new FluidResourceIndexer().index(scan,localization,tags,"en_us",diagnostics));
        report(progress,"Parsing recipes...",0.8);
        final var recipes=new StaticRecipeScanner(integration.parsers(),integration.layout()).scan(scan.documents(),diagnostics);
        report(progress,"Building search indexes...",0.95);
        final var index=new RecipeIndex(recipes,tags);
        index.conflicts().forEach((id,values)->diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,id.toString(),
                "Conflicting recipe definitions: "+values.size()+"; all sources retained, active winner unknown")));
        final long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
        final var result=new DiscoveryResult(pack.selectedDirectory(),new RegistryIndex(resources,scan),index,diagnostics,elapsed);
        report(progress,"Completed.",1);
        logger.info("Indexed {}: {} resources, {} recipes, {} conflicts in {} ms",pack.selectedDirectory(),resources.size(),recipes.size(),index.conflicts().size(),elapsed);
        return result;
    }
    private void report(final Consumer<ImportProgress> consumer,final String stage,final double fraction) {
        SafeFiles.checkCancelled(); consumer.accept(new ImportProgress(stage,0,0,fraction));
    }
    @Override public void close() { active.forEach(DiscoveryOperation::cancel); worker.shutdownNow(); }
}
