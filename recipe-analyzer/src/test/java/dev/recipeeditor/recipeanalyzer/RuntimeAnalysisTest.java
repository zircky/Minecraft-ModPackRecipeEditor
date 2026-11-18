package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

final class RuntimeAnalysisTest {
  private final AnalysisFixtures f = new AnalysisFixtures();

  private RuntimeData.Snapshot snapshot(final String mc) {
    final var properties = new com.google.gson.JsonObject();
    final var ingredients = new com.google.gson.JsonArray();
    final var item = new com.google.gson.JsonObject();
    item.addProperty("item", "test:input");
    ingredients.add(item);
    properties.add("ingredients", ingredients);
    final var output = new com.google.gson.JsonObject();
    output.addProperty("item", "test:runtime_output");
    output.addProperty("count", 1);
    properties.add("result", output);
    return new RuntimeData.Snapshot(
        1,
        "unit-snapshot",
        true,
        new RuntimeData.Environment(
            "unit-session",
            "unit-instance",
            mc,
            "FORGE",
            "47.4.10",
            "0.1.0",
            1,
            "2026-11-17T00:00:00Z"),
        List.of(),
        List.of(new RuntimeData.Tag(RuntimeData.Kind.ITEM, "test:empty", List.of())),
        List.of(
            new RuntimeData.Recipe(
                "test:r",
                "minecraft:crafting",
                "minecraft:crafting_shapeless",
                "UnitRecipe",
                "vanilla-1.20.1",
                RuntimeData.Support.SUPPORTED,
                properties,
                "Explicit unit DTO, not actual Minecraft",
                List.of())),
        List.of(),
        List.of());
  }

  private AnalysisContext context(
      final RuntimeData.Snapshot snapshot,
      final List<RecipeDefinition> originals,
      final RecipeChangeSet changes) {
    return new AnalysisContext(
        new AnalysisEnvironment(
            "1.20.1",
            "FORGE",
            Map.of(),
            AnalysisEnvironment.Scope.SNAPSHOT_PROJECT,
            Instant.parse("2026-11-18T12:00:00Z")),
        new RecipeIndex(originals, new TagIndex(List.of())),
        new RegistryIndex(List.of()),
        snapshot,
        changes,
        Map.of(),
        new AnalysisSettings(),
        List.of(),
        new RecipeGraphIndex(),
        () -> false);
  }

  @Test
  void explicitSnapshotScopePrefersRuntimeAndWarnsAboutAge() {
    final var context =
        context(
            snapshot("1.20.1"),
            List.of(f.recipe("test:r", "test:old_input", "test:old_output")),
            new RecipeChangeSet());
    final var graph = context.dependencyGraph();
    assertTrue(graph.nodes().containsKey(f.item("test:runtime_output")));
    assertFalse(graph.nodes().containsKey(f.item("test:old_output")));
    assertTrue(
        context.dataset().diagnostics().stream()
            .anyMatch(d -> d.code().equals("STALE_RUNTIME_SNAPSHOT")));
    assertEquals(
        TagResolution.Status.EMPTY_RUNTIME,
        new AnalysisTagResolver(context)
            .resolve(new NodeKey(DependencyNodeType.TAG, "item", "test:empty"))
            .status());
  }

  @Test
  void incompatibleSnapshotCannotOverrideStaticOriginal() {
    final var context =
        context(
            snapshot("1.21"),
            List.of(f.recipe("test:r", "test:old_input", "test:old_output")),
            new RecipeChangeSet());
    assertFalse(context.usesSnapshot());
    assertTrue(context.dependencyGraph().nodes().containsKey(f.item("test:old_output")));
    assertTrue(
        context.dataset().diagnostics().stream()
            .anyMatch(d -> d.code().equals("SNAPSHOT_ENVIRONMENT_MISMATCH")));
  }

  @Test
  void projectDeleteAndUpdateTakePrecedenceOverRuntime() {
    final var original = f.recipe("test:r", "test:old_input", "test:old_output");
    final var validator =
        new RecipeValidator(
            new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),
            new RegistryIndex(List.of()));
    final var editor =
        new RecipeEditorService(
            new RecipeIndex(List.of(original), new TagIndex(List.of())), validator);
    editor.delete(original.id());
    assertTrue(
        context(snapshot("1.20.1"), List.of(original), editor.snapshot())
            .dataset()
            .recipes()
            .isEmpty());
    editor.undo();
    editor.update(original.id(), f.recipe("test:r", "test:input", "test:project_output"));
    final var graph =
        context(snapshot("1.20.1"), List.of(original), editor.snapshot()).dependencyGraph();
    assertTrue(graph.nodes().containsKey(f.item("test:project_output")));
    assertFalse(graph.nodes().containsKey(f.item("test:runtime_output")));
  }

  @Test
  void malformedRuntimePropertiesRetainRecipeMetadataAsOpaque() {
    final var source = snapshot("1.20.1");
    final var dto = source.recipes().getFirst();
    final var broken = new com.google.gson.JsonObject();
    broken.addProperty("ingredients", "not-an-array");
    final var record =
        new RuntimeData.Recipe(
            dto.id(),
            dto.type(),
            dto.serializer(),
            dto.runtimeClass(),
            dto.adapter(),
            RuntimeData.Support.SUPPORTED,
            broken,
            dto.sourceInformation(),
            List.of());
    final var snap =
        new RuntimeData.Snapshot(
            1,
            source.snapshotId(),
            true,
            source.environment(),
            List.of(),
            List.of(),
            List.of(record),
            List.of(),
            List.of());
    final var context = context(snap, List.of(), new RecipeChangeSet());
    assertEquals("test:r", context.dataset().recipes().getFirst().id());
    assertTrue(context.dependencyGraph().hasUnknownDependencies());
  }
}
