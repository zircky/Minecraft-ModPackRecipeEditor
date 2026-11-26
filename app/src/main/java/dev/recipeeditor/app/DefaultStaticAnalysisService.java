package dev.recipeeditor.app;

import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.integrations.MinecraftCompatibilityRegistry;
import dev.recipeeditor.integrations.RecipeFormatAdapter;
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
    private final MinecraftCompatibilityRegistry compatibility = new MinecraftCompatibilityRegistry();
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
        final var selected = compatibility.find(pack.minecraftVersion().value());
        if (selected.isEmpty())
            diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, "Version", "No matching recipe format. Only resources will be indexed."));
        else if (!selected.orElseThrow().editingSupported())
            diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, "Version", "Recipe discovery only for " + pack.minecraftVersion().value()
                    + ". Programmatically registered recipes require a separate adapter or runtime evidence."));
        final var layout = selected.map(RecipeFormatAdapter::layout).orElseGet(ResourceLayout::noRecipeFiles);
        final List<RecipeParser> parsers = selected.map(RecipeFormatAdapter::parsers).orElseGet(List::of);
        final var scan=new ResourceScanner(new DiscoveryLimits(),layout).scan(pack,progress);
        diagnostics.addAll(scan.diagnostics());
        final var localization=new LocalizationService(scan.documents()); diagnostics.addAll(localization.diagnostics());
        final var tags=new TagIndex(scan.documents(), layout); diagnostics.addAll(tags.diagnostics());
        final var semanticNames=new RecipeSemanticLocalization();
        final var recipes=new StaticRecipeScanner(parsers,layout).scan(scan.documents(),diagnostics).stream().map(recipe->semanticNames.localize(recipe,localization)).toList();
        final var references=new RecipeResourceReferences().collect(recipes);
        final var resources=new ArrayList<RegistryEntry>();
        report(progress,"Indexing items...",0.55);
        resources.addAll(new ItemResourceIndexer().index(scan,localization,tags,"en_us",diagnostics,references));
        report(progress,"Indexing blocks...",0.65);
        resources.addAll(new BlockResourceIndexer().index(scan,localization,tags,"en_us",diagnostics));
        report(progress,"Indexing fluids...",0.72);
        resources.addAll(new FluidResourceIndexer().index(scan,localization,tags,"en_us",diagnostics,references));
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
