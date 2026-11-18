package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.io.SafeFiles;

import dev.recipeeditor.core.importing.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serial background imports; all input resources belong to the worker, never to JavaFX. */
public final class DefaultModpackImportService implements ModpackImportService {
    private final Logger logger = LoggerFactory.getLogger(DefaultModpackImportService.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("modpack-import-",0).factory());
    private final ImportLimits limits;
    private final ModScanner scanner;
    private final Set<ImportOperation> active = ConcurrentHashMap.newKeySet();

    public DefaultModpackImportService() { this(new ImportLimits()); }
    public DefaultModpackImportService(final ImportLimits limits) {
        this.limits = Objects.requireNonNull(limits);
        scanner = new ModScanner(limits, List.of(new ForgeMetadataParser(),new NeoForgeMetadataParser(),new FabricMetadataParser(),new QuiltMetadataParser()));
    }
    @Override
    public ImportOperation start(final Path directory, final Consumer<ImportProgress> progress) {
        Objects.requireNonNull(directory); Objects.requireNonNull(progress);
        final var result = new CompletableFuture<ModpackImportResult>();
        final var task = new FutureTask<Void>(() -> {
            try { result.complete(importDirectory(directory,progress)); }
            catch (CancellationException error) { result.cancel(false); }
            catch (Exception error) { logger.warn("Import failed: {}",directory,error); result.completeExceptionally(error); }
            return null;
        });
        final var operation = new ImportOperation() {
            @Override public CompletableFuture<ModpackImportResult> result() { return result; }
            @Override public void cancel() { task.cancel(true); result.cancel(false); }
        };
        active.add(operation);
        result.whenComplete((value,error) -> active.remove(operation));
        try { executor.execute(task); }
        catch (RejectedExecutionException error) { result.completeExceptionally(error); }
        return operation;
    }
    private ModpackImportResult importDirectory(final Path directory, final Consumer<ImportProgress> progress) throws IOException {
        report(progress,"Detecting modpack...",0,0,0);
        final var detected = new ModpackDetector().detect(directory);
        if (!detected.recognized()) throw new IOException(detected.diagnostics().getLast().message());
        final var diagnostics = new ArrayList<>(detected.diagnostics());
        final var evidence = new InstanceMetadataReader().read(detected);
        diagnostics.addAll(evidence.diagnostics());
        report(progress,"Detecting Minecraft version...",0,0,0.05);
        final var minecraft = new MinecraftVersionDetector().detect(evidence,diagnostics);
        report(progress,"Detecting mod loader...",0,0,0.1);
        final var loader = new ModLoaderDetector().detect(evidence,diagnostics);
        report(progress,"Scanning JAR files...",0,0,0.15);
        final var jars = listJars(detected);
        final var results = new ArrayList<ModScanResult>();
        final var budget = new ModScanner.ReadBudget(limits.maxTotalMetadataBytes());
        for (int i = 0; i < jars.size(); i++) {
            report(progress,"Parsing mod metadata...",i,jars.size(),0.2 + 0.65 * i / Math.max(1,jars.size()));
            if (budget.exhausted()) {
                diagnostics.add(new ImportDiagnostic(ScanStatus.ERROR,"Import","Total metadata budget exhausted; remaining JARs were not scanned"));
                break;
            }
            final var scanned = scanner.scan(detected.selectedDirectory(),jars.get(i),budget);
            results.add(scanned); diagnostics.addAll(scanned.diagnostics());
        }
        report(progress,"Resolving dependencies...",results.size(),jars.size(),0.9);
        final var pack = new ModpackDefinition(detected.selectedDirectory(),detected.gameDirectory(),detected.structure(),minecraft,loader,results);
        diagnostics.addAll(new DependencyValidator().validate(pack.mods(),loader));
        report(progress,"Finishing import...",results.size(),jars.size(),1);
        logger.info("Imported {}: Minecraft {}, loader {}, {} JARs, {} mod definitions", directory,minecraft.value(),loader.type(),results.size(),pack.mods().size());
        return new ModpackImportResult(pack,diagnostics);
    }
    private List<Path> listJars(final DetectionResult detected) throws IOException {
        final var directory = detected.gameDirectory().resolve("mods");
        if (!Files.exists(directory)) return List.of();
        final var safe = SafeFiles.inside(detected.selectedDirectory(),directory);
        final var result = new ArrayList<Path>();
        int entries = 0;
        try (final var stream = Files.newDirectoryStream(safe)) {
            for (final var path : stream) {
                SafeFiles.checkCancelled();
                if (++entries > limits.maxJars() * 4L) throw new IOException("Too many entries in mods directory");
                if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    if (result.size() >= limits.maxJars()) throw new IOException("Too many JAR files; maximum " + limits.maxJars());
                    result.add(path);
                }
            }
        }
        result.sort(Comparator.comparing(Path::toString));
        return result;
    }
    private void report(final Consumer<ImportProgress> consumer, final String stage, final int processed, final int total, final double fraction) {
        SafeFiles.checkCancelled(); consumer.accept(new ImportProgress(stage,processed,total,fraction));
    }
    @Override public void close() { active.forEach(ImportOperation::cancel); executor.shutdownNow(); }
}
