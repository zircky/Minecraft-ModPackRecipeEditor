package dev.recipeeditor.recipeanalyzer.api;

import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.integrations.api.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeanalyzer.model.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Capture once, use on a background worker. No disk IO, UI dependencies or live-world claims. */
public final class AnalysisContext {
  private final AnalysisEnvironment environment;
  private final RecipeIndex originals;
  private final RegistryIndex registry;
  private final RuntimeData.Snapshot snapshot;
  private final RecipeChangeSet changes;
  private final Map<String, IntegrationCapabilities> capabilities;
  private final AnalysisSettings settings;
  private final List<RecipeDependencyAdapter> adapters;
  private final RecipeGraphIndex graphIndex;
  private final BooleanSupplier cancelled;
  private AnalysisDataset dataset;
  private RecipeDependencyGraph graph;

  public AnalysisContext(
      final AnalysisEnvironment environment,
      final RecipeIndex originals,
      final RegistryIndex registry,
      final RuntimeData.Snapshot snapshot,
      final RecipeChangeSet changes,
      final Map<String, IntegrationCapabilities> capabilities,
      final AnalysisSettings settings,
      final List<RecipeDependencyAdapter> adapters,
      final RecipeGraphIndex graphIndex,
      final BooleanSupplier cancelled) {
    this.environment = Objects.requireNonNull(environment);
    this.originals = Objects.requireNonNull(originals);
    this.registry = Objects.requireNonNull(registry);
    this.snapshot = snapshot;
    this.changes = Objects.requireNonNull(changes);
    this.capabilities = Map.copyOf(capabilities);
    this.settings = Objects.requireNonNull(settings);
    this.adapters = List.copyOf(adapters);
    this.graphIndex = Objects.requireNonNull(graphIndex);
    this.cancelled = Objects.requireNonNull(cancelled);
    if (environment.scope() == AnalysisEnvironment.Scope.SNAPSHOT_PROJECT && snapshot == null)
      throw new IllegalArgumentException("Snapshot scope requires a snapshot");
  }

  public static AnalysisContext staticProject(
      final String minecraft,
      final String loader,
      final Map<String, String> mods,
      final RecipeIndex originals,
      final RegistryIndex registry,
      final RecipeChangeSet changes) {
    final dev.recipeeditor.core.importing.ModLoaderType type;
    try {
      type = dev.recipeeditor.core.importing.ModLoaderType.valueOf(loader);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Unknown loader", invalid);
    }
    final var capabilities =
        new dev.recipeeditor.integrations.gregtech.GregTechIntegration()
            .capabilities(new IntegrationContext(minecraft, type, mods, Map.of()));
    return new AnalysisContext(
        new AnalysisEnvironment(
            minecraft,
            loader,
            mods,
            AnalysisEnvironment.Scope.STATIC_PROJECT,
            java.time.Instant.now()),
        originals,
        registry,
        null,
        changes,
        Map.of("gtceu", capabilities),
        new AnalysisSettings(),
        List.of(),
        new RecipeGraphIndex(),
        () -> false);
  }

  public AnalysisEnvironment environment() {
    return environment;
  }

  public RecipeIndex originals() {
    return originals;
  }

  public RegistryIndex registry() {
    return registry;
  }

  public RuntimeData.Snapshot runtimeSnapshot() {
    return snapshot;
  }

  public RecipeChangeSet changes() {
    return changes;
  }

  public Map<String, IntegrationCapabilities> capabilities() {
    return capabilities;
  }

  public AnalysisSettings settings() {
    return settings;
  }

  public List<RecipeDependencyAdapter> adapters() {
    return adapters;
  }

  public void checkCancelled() {
    if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
      throw new java.util.concurrent.CancellationException("Analysis cancelled");
  }

  public boolean snapshotCompatible() {
    if (snapshot == null
        || !snapshot.complete()
        || !environment.minecraftVersion().equals(snapshot.environment().minecraftVersion())
        || !environment.loader().equals(snapshot.environment().loader())) return false;
    final var actual = new HashMap<String, String>();
    snapshot.mods().forEach(mod -> actual.put(mod.id(), mod.version()));
    for (final var mod : environment.mods().entrySet())
      if (!mod.getValue().equals(actual.get(mod.getKey()))) return false;
    return true;
  }

  public boolean usesSnapshot() {
    return environment.scope() == AnalysisEnvironment.Scope.SNAPSHOT_PROJECT
        && snapshotCompatible();
  }

  public synchronized AnalysisDataset dataset() {
    checkCancelled();
    if (dataset == null) dataset = new EffectiveAnalysisInput().prepare(this);
    return dataset;
  }

  public synchronized RecipeDependencyGraph dependencyGraph() {
    checkCancelled();
    if (graph == null) graph = new RecipeGraphBuilder().build(this, graphIndex);
    return graph;
  }

  public AnalysisResult result(
      final String id,
      final boolean complete,
      final Map<String, Long> metrics,
      final List<AnalysisDiagnostic> diagnostics) {
    return new AnalysisResult(
        id,
        "1",
        environment.timestamp(),
        snapshot == null ? "" : snapshot.snapshotId(),
        complete && !dataset().truncated(),
        metrics,
        diagnostics);
  }
}
