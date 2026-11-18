package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.companionclient.RuntimeSnapshotService;
import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.integrations.api.*;
import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@org.junit.jupiter.api.Tag("external")
final class AnalyzerSnapshotIntegrationTest {
  @Test
  @EnabledIfSystemProperty(named = "analyzerSnapshot", matches = ".+")
  void analyzesActualPreviouslyCapturedMinecraftSnapshot() throws Exception {
    final long started = System.nanoTime();
    final var file = Path.of(System.getProperty("analyzerSnapshot"));
    final var snapshot = new RuntimeSnapshotService().load(file);
    final var mods = new LinkedHashMap<String, String>();
    snapshot.mods().forEach(mod -> mods.put(mod.id(), mod.version()));
    final var gt =
        new dev.recipeeditor.integrations.gregtech.GregTechIntegration()
            .capabilities(
                new IntegrationContext(
                    snapshot.environment().minecraftVersion(),
                    ModLoaderType.FORGE,
                    mods,
                    Map.of()));
    final var context =
        new AnalysisContext(
            new AnalysisEnvironment(
                snapshot.environment().minecraftVersion(),
                snapshot.environment().loader(),
                mods,
                AnalysisEnvironment.Scope.SNAPSHOT_PROJECT,
                Instant.now()),
            new RecipeIndex(List.of(), new TagIndex(List.of())),
            new RegistryIndex(List.of()),
            snapshot,
            new RecipeChangeSet(),
            Map.of("gtceu", gt),
            new AnalysisSettings(),
            List.of(),
            new RecipeGraphIndex(),
            () -> false);
    final var registry = new DefaultAnalyzers().registry();
    final var results = registry.analyze(context, Set.copyOf(registry.ids()));
    final var graph = context.dependencyGraph();
    assertTrue(context.dataset().recipes().size() >= snapshot.recipes().size());
    assertFalse(graph.truncated(), "Default graph budgets truncated the actual snapshot");
    assertTrue(graph.nodes().keySet().stream().anyMatch(n -> n.type() == DependencyNodeType.FLUID));
    assertTrue(graph.nodes().keySet().stream().anyMatch(n -> n.type() == DependencyNodeType.TAG));
    assertTrue(
        graph.edges().stream()
            .anyMatch(e -> e.consumption() == DependencyEdge.Consumption.CATALYST));
    assertTrue(
        results.stream()
            .flatMap(r -> r.diagnostics().stream())
            .noneMatch(d -> d.severity() == AnalysisDiagnostic.Severity.ERROR));
    final var output = Path.of("build/phase8-snapshot-result.txt");
    Files.createDirectories(output.getParent());
    final var text =
        new StringBuilder(
            "Analysis of actual previously captured Minecraft snapshot; Minecraft was NOT launched"
                + " in this test.\n"
                + "Snapshot="
                + snapshot.snapshotId()
                + "\nMC="
                + snapshot.environment().minecraftVersion()
                + " loader="
                + snapshot.environment().loader()
                + " mods="
                + mods
                + "\nCaptured recipes="
                + snapshot.recipes().size()
                + " analyzed variants="
                + context.dataset().recipes().size()
                + "\nNodes="
                + graph.nodes().size()
                + " edges="
                + graph.edges().size()
                + " unknownDependencies="
                + graph.hasUnknownDependencies()
                + "\n");
    results.forEach(
        result ->
            text.append(result.analyzerId())
                .append(" complete=")
                .append(result.complete())
                .append(" metrics=")
                .append(result.metrics())
                .append(" diagnostics=")
                .append(result.diagnostics().size())
                .append('\n'));
    text.append("Elapsed ms=").append((System.nanoTime() - started) / 1000000).append('\n');
    Files.writeString(output, text);
    System.out.println(text);
  }
}
