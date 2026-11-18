package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeanalyzer.model.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

final class RecipeDependencyGraphTest {
  private final AnalysisFixtures f = new AnalysisFixtures();

  @Test
  void multipleInputsAndOutputsStayAttachedToOneVariantRecipeNode() {
    final String raw =
        f.shapeless("test:a", "test:result", 2)
            .replace("[{\"item\":\"test:a\"}]", "[{\"item\":\"test:a\"},{\"item\":\"test:b\"}]");
    final var graph = f.context(List.of(f.recipe("test:multi", raw))).dependencyGraph();
    final var recipe =
        graph.nodes().keySet().stream()
            .filter(n -> n.type() == DependencyNodeType.RECIPE)
            .findFirst()
            .orElseThrow();
    assertEquals(2, graph.incoming(recipe).size());
    assertEquals(1, graph.outgoing(recipe).size());
    assertEquals(BigDecimal.valueOf(2), graph.outgoing(recipe).getFirst().quantity());
    assertEquals(3, graph.edges().size());
  }

  @Test
  void itemFluidAndChemicalMaterialNamespacesAreDistinctThroughExplicitAdapter() {
    final var definition =
        f.recipe("test:custom", "{\"type\":\"example:processor\",\"unknown\":true}");
    final RecipeDependencyAdapter adapter =
        new RecipeDependencyAdapter() {
          public boolean supports(dev.recipeeditor.recipeengine.RecipeDefinition recipe) {
            return recipe.type().displayId().equals("example:processor");
          }

          public ListPorts describe(dev.recipeeditor.recipeengine.RecipeDefinition recipe) {
            final var ports = new ArrayList<RecipePort>();
            int i = 0;
            for (final var key :
                List.of(
                    f.item("minecraft:water"),
                    new NodeKey(DependencyNodeType.FLUID, "minecraft-fluid", "minecraft:water"),
                    new NodeKey(DependencyNodeType.CHEMICAL, "gas", "test:chemical"),
                    new NodeKey(DependencyNodeType.CHEMICAL, "infuse-type", "test:chemical"),
                    new NodeKey(DependencyNodeType.MATERIAL, "material-registry", "test:material")))
              ports.add(
                  new RecipePort(
                      "slot/" + i++,
                      new DependencyIngredient(key, BigDecimal.ONE, List.of(), ""),
                      DependencyEdge.Consumption.CONSUMED,
                      DependencyEdge.Cadence.PER_RECIPE,
                      null,
                      null,
                      null));
            return new ListPorts(ports, List.of(), true, List.of());
          }
        };
    final var graph =
        f.context(
                List.of(definition),
                new RecipeChangeSet(),
                new TagIndex(List.of()),
                new RecipeGraphIndex(),
                new AnalysisSettings(),
                List.of(adapter))
            .dependencyGraph();
    assertEquals(6, graph.nodes().size());
    assertEquals(5, graph.edges().size());
    assertNotEquals(
        f.item("minecraft:water"),
        new NodeKey(DependencyNodeType.FLUID, "minecraft-fluid", "minecraft:water"));
  }

  @Test
  void tagCandidatesAreNotCollapsedAndMissingStaticTagIsNotAnError() {
    final var recipe =
        f.recipe(
            "test:tag",
            f.shapeless("test:a", "test:c", 1)
                .replace("\"item\":\"test:a\"", "\"tag\":\"test:metals\""));
    final var graph =
        f.context(
                List.of(recipe),
                new RecipeChangeSet(),
                f.tags("metals", "[\"test:a\",\"test:b\"]"),
                new RecipeGraphIndex(),
                new AnalysisSettings(),
                List.of())
            .dependencyGraph();
    assertTrue(graph.nodes().containsKey(f.item("test:a")));
    assertTrue(graph.nodes().containsKey(f.item("test:b")));
    assertEquals(
        2, graph.edges().stream().filter(e -> e.role() == DependencyEdge.Role.TAG_MEMBER).count());
    final var missing = f.context(List.of(recipe)).dependencyGraph();
    assertTrue(missing.diagnostics().stream().anyMatch(d -> d.code().equals("UNRESOLVED_TAG")));
    assertTrue(
        missing.diagnostics().stream()
            .noneMatch(d -> d.severity() == AnalysisDiagnostic.Severity.ERROR));
  }

  @Test
  void alternativesAreOrGroupsAndGtCatalystAndChanceArePreserved() {
    final String alternatives =
        f.shapeless("test:a", "test:b", 1)
            .replace("{\"item\":\"test:a\"}", "[{\"item\":\"test:a\"},{\"item\":\"test:c\"}]");
    final var graph = f.context(List.of(f.recipe("test:choice", alternatives))).dependencyGraph();
    assertEquals(
        1,
        graph.nodes().keySet().stream().filter(n -> n.type() == DependencyNodeType.CHOICE).count());
    assertEquals(
        2, graph.edges().stream().filter(e -> e.role() == DependencyEdge.Role.ALTERNATIVE).count());
    final String gt =
        "{\"type\":\"gtceu:assembler\",\"duration\":20,\"inputs\":{\"item\":[{\"content\":{\"type\":\"gtceu:sized\",\"count\":2,\"ingredient\":{\"item\":\"test:a\"}},\"chance\":0,\"maxChance\":10000}]},\"outputs\":{\"item\":[{\"content\":{\"item\":\"test:b\"},\"chance\":5000,\"maxChance\":10000,\"tierChanceBoost\":100}]}}";
    final var gtGraph = f.context(List.of(f.recipe("test:gt", gt))).dependencyGraph();
    final var input =
        gtGraph.edges().stream()
            .filter(e -> e.role() == DependencyEdge.Role.INPUT)
            .findFirst()
            .orElseThrow();
    assertEquals(DependencyEdge.Consumption.CATALYST, input.consumption());
    assertEquals(BigDecimal.valueOf(2), input.quantity());
    final var output =
        gtGraph.edges().stream()
            .filter(e -> e.role() == DependencyEdge.Role.OUTPUT)
            .findFirst()
            .orElseThrow();
    assertEquals(5000, output.chance());
    assertEquals(100, output.boost());
    assertFalse(output.guaranteedOutput());
    assertTrue(
        gtGraph.nodes().keySet().stream().anyMatch(n -> n.type() == DependencyNodeType.MACHINE));
  }

  @Test
  void incrementalDeleteReusesUnchangedFragmentsAndKeepsOldSnapshotsImmutable() {
    final var one = f.recipe("test:one", "test:a", "test:b");
    final var two = f.recipe("test:two", "test:b", "test:c");
    final var index = new RecipeGraphIndex();
    final var before =
        f.context(
                List.of(one, two),
                new RecipeChangeSet(),
                new TagIndex(List.of()),
                index,
                new AnalysisSettings(),
                List.of())
            .dependencyGraph();
    final var after =
        f.context(
                List.of(two),
                new RecipeChangeSet(),
                new TagIndex(List.of()),
                index,
                new AnalysisSettings(),
                List.of())
            .dependencyGraph();
    assertEquals(1, after.reusedFragments());
    assertEquals(1, after.removedFragments());
    assertEquals(0, after.addedFragments());
    assertFalse(after.nodes().containsKey(f.item("test:a")));
    assertTrue(before.nodes().containsKey(f.item("test:a")));
    final var reach =
        new ReverseDependencyIndex().dependents(before, f.item("test:a"), 10, 100, () -> false);
    assertEquals(2, reach.recipes().size());
    assertTrue(reach.nodes().contains(f.item("test:c")));
    assertTrue(
        new ReverseDependencyIndex()
            .dependents(before, f.item("test:a"), 1, 100, () -> false)
            .truncated());
  }

  @Test
  void graphAndTagBudgetsProduceVisiblePartialResults() {
    final var recipe =
        f.recipe(
            "test:tag",
            f.shapeless("test:a", "test:c", 1)
                .replace("\"item\":\"test:a\"", "\"tag\":\"test:metals\""));
    final var settings =
        new AnalysisSettings(10, 100, 100, 1, 10, 5, 100, java.time.Duration.ofMinutes(1));
    final var graph =
        f.context(
                List.of(recipe),
                new RecipeChangeSet(),
                f.tags("metals", "[\"test:a\",\"test:b\"]"),
                new RecipeGraphIndex(),
                settings,
                List.of())
            .dependencyGraph();
    assertTrue(graph.truncated());
    assertTrue(graph.diagnostics().stream().anyMatch(d -> d.code().equals("TAG_EXPANSION_LIMIT")));
    final var tiny =
        new AnalysisSettings(10, 1, 1, 10, 10, 5, 100, java.time.Duration.ofMinutes(1));
    assertTrue(
        f.context(
                List.of(recipe),
                new RecipeChangeSet(),
                new TagIndex(List.of()),
                new RecipeGraphIndex(),
                tiny,
                List.of())
            .dependencyGraph()
            .truncated());
  }

  private RecipeDependencyAdapter outputAdapter(final String output) {
    return new RecipeDependencyAdapter() {
      public boolean supports(dev.recipeeditor.recipeengine.RecipeDefinition recipe) {
        return recipe.type().displayId().equals("example:custom");
      }

      public ListPorts describe(dev.recipeeditor.recipeengine.RecipeDefinition recipe) {
        return new ListPorts(
            List.of(),
            List.of(
                new RecipePort(
                    "output",
                    new DependencyIngredient(f.item(output), BigDecimal.ONE, List.of(), ""),
                    DependencyEdge.Consumption.NOT_APPLICABLE,
                    DependencyEdge.Cadence.PER_RECIPE,
                    1,
                    1,
                    null)),
            true,
            List.of());
      }
    };
  }

  @Test
  void changingAdapterSemanticsInvalidatesAnUnchangedRawRecipe() {
    final var definition = f.recipe("test:custom", "{\"type\":\"example:custom\"}");
    final var index = new RecipeGraphIndex();
    final var before =
        f.context(
                List.of(definition),
                new RecipeChangeSet(),
                new TagIndex(List.of()),
                index,
                new AnalysisSettings(),
                List.of(outputAdapter("test:a")))
            .dependencyGraph();
    final var after =
        f.context(
                List.of(definition),
                new RecipeChangeSet(),
                new TagIndex(List.of()),
                index,
                new AnalysisSettings(),
                List.of(outputAdapter("test:b")))
            .dependencyGraph();
    assertTrue(before.nodes().containsKey(f.item("test:a")));
    assertFalse(after.nodes().containsKey(f.item("test:a")));
    assertTrue(after.nodes().containsKey(f.item("test:b")));
    assertEquals(1, after.removedFragments());
    assertThrows(
        dev.recipeeditor.recipeanalyzer.model.DependencyAdapterConflictException.class,
        () ->
            f.context(
                    List.of(definition),
                    new RecipeChangeSet(),
                    new TagIndex(List.of()),
                    new RecipeGraphIndex(),
                    new AnalysisSettings(),
                    List.of(outputAdapter("test:a"), outputAdapter("test:b")))
                .dataset());
  }
}
