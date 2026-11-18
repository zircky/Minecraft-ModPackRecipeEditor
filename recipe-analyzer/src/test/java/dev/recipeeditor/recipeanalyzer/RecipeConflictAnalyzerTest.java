package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.conflict.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeengine.editor.RecipeChangeSet;
import dev.recipeeditor.registry.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class RecipeConflictAnalyzerTest {
  private final AnalysisFixtures f = new AnalysisFixtures();

  @Test
  void fullyKnownVanillaMatchingOverlapIsConfirmedButNotAnError() {
    final var result =
        new RecipeConflictAnalyzer()
            .analyze(
                f.context(
                    List.of(
                        f.recipe("test:one", "test:a", "test:b"),
                        f.recipe("test:two", "test:a", "test:c"))));
    assertEquals(1, result.metrics().get("confirmedMatcherOverlaps"));
    assertTrue(
        result.diagnostics().stream()
            .noneMatch(d -> d.severity() == AnalysisDiagnostic.Severity.ERROR));
  }

  @Test
  void sharingOnlyOneIngredientDoesNotEstablishConflict() {
    final String one =
        f.shapeless("test:a", "test:b", 1)
            .replace("[{\"item\":\"test:a\"}]", "[{\"item\":\"test:a\"},{\"item\":\"test:x\"}]");
    final String two = one.replace("test:x", "test:y").replace("test:b", "test:c");
    final var result =
        new RecipeConflictAnalyzer()
            .analyze(f.context(List.of(f.recipe("test:one", one), f.recipe("test:two", two))));
    assertEquals(0, result.metrics().get("confirmedMatcherOverlaps"));
    assertEquals(0, result.metrics().get("potentialOverlaps"));
  }

  @Test
  void gtAndUnknownSelectionRulesRemainPotential() {
    final String gt =
        "{\"type\":\"gtceu:assembler\",\"duration\":100,\"inputs\":{\"item\":[{\"content\":{\"item\":\"test:a\"},\"chance\":10000,\"maxChance\":10000}]},\"outputs\":{\"item\":[{\"content\":{\"item\":\"test:b\"}}]}}";
    final var result =
        new RecipeConflictAnalyzer()
            .analyze(
                f.context(
                    List.of(
                        f.recipe("test:one", gt),
                        f.recipe("test:two", gt.replace("test:b", "test:c")))));
    assertEquals(0, result.metrics().get("confirmedMatcherOverlaps"));
    assertEquals(1, result.metrics().get("potentialOverlaps"));
    final var unknown =
        new RecipeConflictAnalyzer(List.of())
            .analyze(
                f.context(
                    List.of(
                        f.recipe("test:one", "test:a", "test:b"),
                        f.recipe("test:two", "test:a", "test:c"))));
    assertEquals(1, unknown.metrics().get("potentialOverlaps"));
  }

  @Test
  void staticTagAndConcreteAlternativeOverlapIsPotential() {
    final var tag =
        f.recipe(
            "test:tag",
            f.shapeless("test:a", "test:b", 1)
                .replace("\"item\":\"test:a\"", "\"tag\":\"test:metals\""));
    final var context =
        f.context(
            List.of(tag, f.recipe("test:item", "test:a", "test:c")),
            new RecipeChangeSet(),
            f.tags("metals", "[\"test:a\",\"test:other\"]"),
            new RecipeGraphIndex(),
            new AnalysisSettings(),
            List.of());
    final var result = new RecipeConflictAnalyzer().analyze(context);
    assertEquals(1, result.metrics().get("potentialOverlaps"));
    assertEquals(0, result.metrics().get("confirmedMatcherOverlaps"));
  }

  @Test
  void equalMissingTagsArePotentialRatherThanConfirmed() {
    final String a =
        f.shapeless("test:a", "test:b", 1)
            .replace("\"item\":\"test:a\"", "\"tag\":\"test:missing\"");
    final var result =
        new RecipeConflictAnalyzer()
            .analyze(
                f.context(
                    List.of(
                        f.recipe("test:one", a),
                        f.recipe("test:two", a.replace("test:b", "test:c")))));
    assertEquals(0, result.metrics().get("confirmedMatcherOverlaps"));
    assertEquals(1, result.metrics().get("potentialOverlaps"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RecipeConflictAnalyzer(
                    List.of(new VanillaConflictStrategy(), new VanillaConflictStrategy()))
                .analyze(
                    f.context(
                        List.of(
                            f.recipe("test:a", "test:x", "test:y"),
                            f.recipe("test:b", "test:x", "test:z")))));
  }
}
