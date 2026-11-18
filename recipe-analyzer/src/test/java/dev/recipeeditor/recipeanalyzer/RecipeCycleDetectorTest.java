package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeengine.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class RecipeCycleDetectorTest {
  private final AnalysisFixtures f = new AnalysisFixtures();

  @Test
  void acyclicChainHasOnlySingletonComponents() {
    final var context =
        f.context(
            List.of(
                f.recipe("test:r1", "test:a", "test:b"), f.recipe("test:r2", "test:b", "test:c")));
    final var detector = new RecipeCycleDetector();
    assertEquals(0, detector.analyze(context).metrics().get("cycles"));
    assertTrue(
        detector.stronglyConnectedComponents(context.dependencyGraph(), () -> false).stream()
            .allMatch(s -> s.size() == 1));
  }

  @Test
  void disconnectedCyclesFormSeparateStronglyConnectedComponents() {
    final var context =
        f.context(
            List.of(
                f.recipe("test:r1", "test:a", "test:b"),
                f.recipe("test:r2", "test:b", "test:a"),
                f.recipe("test:r3", "test:x", "test:y"),
                f.recipe("test:r4", "test:y", "test:x")));
    final var result = new RecipeCycleDetector().analyze(context);
    assertEquals(2, result.metrics().get("cycles"));
    assertTrue(
        result.diagnostics().stream()
            .filter(d -> d.code().equals("DEPENDENCY_CYCLE"))
            .allMatch(
                d ->
                    d.severity()
                        != dev.recipeeditor.recipeanalyzer.api.AnalysisDiagnostic.Severity.ERROR));
  }

  @Test
  void deterministicQuantityGainIsPotentialAndExternalEntryPreventsClosedDeadlock() {
    final var gain =
        new RecipeCycleDetector()
            .analyze(f.context(List.of(f.recipe("test:gain", f.shapeless("test:a", "test:a", 2)))));
    assertEquals(1, gain.metrics().get("potentialResourceLoops"));
    final var seeded =
        f.context(
            List.of(
                f.recipe("test:r1", "test:a", "test:b"),
                f.recipe("test:r2", "test:b", "test:a"),
                f.recipe("test:seed", "test:outside", "test:a")));
    assertEquals(0, new RecipeCycleDetector().analyze(seeded).metrics().get("potentialDeadlocks"));
  }

  @Test
  void deepChainDoesNotUseJvmRecursion() {
    final var source = f.recipe("test:source", "test:a", "test:b").source();
    final var recipes = new ArrayList<RecipeDefinition>();
    for (int i = 0; i < 6000; i++) {
      final String raw = f.shapeless("test:n" + i, "test:n" + (i + 1), 1);
      recipes.add(
          new RecipeDefinition(
              ResourceLocation.parse("test:r" + i),
              new RecipeTypeDefinition("minecraft:crafting_shapeless"),
              List.of(new IngredientDefinition.Item(ResourceLocation.parse("test:n" + i))),
              List.of(
                  new RecipeOutput.Fixed(ResourceLocation.parse("test:n" + (i + 1)), 1, false, "")),
              new RecipeLayout(),
              List.of(),
              source,
              raw,
              RecipeParseStatus.PARSED,
              List.of()));
    }
    final var result = new RecipeCycleDetector().analyze(f.context(recipes));
    assertEquals(0, result.metrics().get("cycles"));
    assertEquals(12001, result.metrics().get("nodes"));
  }
}
