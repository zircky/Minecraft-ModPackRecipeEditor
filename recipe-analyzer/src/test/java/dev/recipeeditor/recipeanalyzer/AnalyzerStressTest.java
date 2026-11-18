package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.core.ResourceLocation;
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

@org.junit.jupiter.api.Tag("performance")
final class AnalyzerStressTest {
  @Test
  @EnabledIfSystemProperty(named = "analyzerStress", matches = "true")
  void sixtyThousandRecipeChainAndSixHundredSyntheticModIdentities() throws Exception {
    final long started = System.nanoTime();
    final var f = new AnalysisFixtures();
    final var source = f.recipe("test:source", "test:a", "test:b").source();
    final var recipes = new ArrayList<RecipeDefinition>();
    final int count = 60000;
    for (int i = 0; i < count; i++) {
      final var input = ResourceLocation.parse("test:n" + i);
      final var output = ResourceLocation.parse("test:n" + (i + 1));
      recipes.add(
          new RecipeDefinition(
              ResourceLocation.parse("test:r" + i),
              new RecipeTypeDefinition("minecraft:crafting_shapeless"),
              List.of(new IngredientDefinition.Item(input)),
              List.of(new RecipeOutput.Fixed(output, 1, false, "")),
              new RecipeLayout(),
              List.of(),
              source,
              f.shapeless(input.toString(), output.toString(), 1),
              RecipeParseStatus.PARSED,
              List.of()));
    }
    final var mods = new HashMap<String, String>();
    for (int i = 0; i < 600; i++) mods.put("synthetic_mod_" + i, "1.0.0");
    final var context =
        new AnalysisContext(
            new AnalysisEnvironment(
                "1.20.1", "FORGE", mods, AnalysisEnvironment.Scope.STATIC_PROJECT, Instant.now()),
            new RecipeIndex(recipes, new TagIndex(List.of())),
            new RegistryIndex(List.of()),
            null,
            new RecipeChangeSet(),
            Map.of(),
            new AnalysisSettings(),
            List.of(),
            new RecipeGraphIndex(),
            () -> false);
    final var registry = new DefaultAnalyzers().registry();
    final var results = registry.analyze(context, Set.copyOf(registry.ids()));
    final var graph = context.dependencyGraph();
    assertEquals(count * 2 + 1, graph.nodes().size());
    assertEquals(count * 2, graph.edges().size());
    assertFalse(graph.truncated());
    assertTrue(results.stream().allMatch(AnalysisResult::complete));
    assertEquals(
        0,
        results.stream()
            .filter(r -> r.analyzerId().equals("cycles"))
            .findFirst()
            .orElseThrow()
            .metrics()
            .get("cycles"));
    final long elapsed = (System.nanoTime() - started) / 1000000,
        used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    final String report =
        "SYNTHETIC load fixture, not an actual 600-mod Minecraft instance.\nRecipes="
            + count
            + " mod identities="
            + mods.size()
            + " nodes="
            + graph.nodes().size()
            + " edges="
            + graph.edges().size()
            + "\nElapsed ms="
            + elapsed
            + "\nUsed heap at end (not peak)="
            + used
            + " bytes\n";
    Files.createDirectories(Path.of("build"));
    Files.writeString(Path.of("build/phase8-stress-result.txt"), report);
    System.out.println(report);
  }
}
