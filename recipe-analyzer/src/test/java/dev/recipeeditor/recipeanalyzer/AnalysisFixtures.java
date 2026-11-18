package dev.recipeeditor.recipeanalyzer;

import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.integrations.*;
import dev.recipeeditor.integrations.api.*;
import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeanalyzer.model.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class AnalysisFixtures {
  private final RecipeValidator validator =
      new RecipeValidator(new ProjectRecipeParsers().parsers(), new RegistryIndex(List.of()));

  public RecipeDefinition recipe(final String id, final String raw) {
    return validator
        .validate(
            id,
            raw,
            new RecipeSource(
                SourceType.LOCAL_RESOURCE,
                List.of("test"),
                Path.of("unit-fixtures"),
                "data/" + id.replace(':', '/') + ".json",
                "Explicit unit fixture"))
        .recipe();
  }

  public String shapeless(final String input, final String output, final int count) {
    return "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\""
        + input
        + "\"}],\"result\":{\"item\":\""
        + output
        + "\",\"count\":"
        + count
        + "}}";
  }

  public RecipeDefinition recipe(final String id, final String input, final String output) {
    return recipe(id, shapeless(input, output, 1));
  }

  public AnalysisContext context(final List<RecipeDefinition> recipes) {
    return context(
        recipes,
        new RecipeChangeSet(),
        new TagIndex(List.of()),
        new RecipeGraphIndex(),
        new AnalysisSettings(),
        List.of());
  }

  public AnalysisContext context(
      final List<RecipeDefinition> recipes,
      final RecipeChangeSet changes,
      final TagIndex tags,
      final RecipeGraphIndex graph,
      final AnalysisSettings settings,
      final List<RecipeDependencyAdapter> adapters) {
    final var mods = Map.of("gtceu", "7.1.4");
    final var caps =
        new dev.recipeeditor.integrations.gregtech.GregTechIntegration()
            .capabilities(new IntegrationContext("1.20.1", ModLoaderType.FORGE, mods, Map.of()));
    return new AnalysisContext(
        new AnalysisEnvironment(
            "1.20.1",
            "FORGE",
            mods,
            AnalysisEnvironment.Scope.STATIC_PROJECT,
            Instant.parse("2026-11-18T12:00:00Z")),
        new RecipeIndex(recipes, tags),
        new RegistryIndex(List.of()),
        null,
        changes,
        Map.of("gtceu", caps),
        settings,
        adapters,
        graph,
        () -> false);
  }

  public NodeKey item(final String id) {
    return new NodeKey(DependencyNodeType.ITEM, "minecraft-item", id);
  }

  public TagIndex tags(final String name, final String values) {
    return new TagIndex(
        List.of(
            new ResourceDocument(
                new ResourceSource(
                    SourceType.LOCAL_RESOURCE,
                    List.of("test"),
                    Path.of("unit-fixtures"),
                    "data/test/tags/items/" + name + ".json",
                    "unit fixture"),
                "{\"values\":" + values + "}")));
  }
}
