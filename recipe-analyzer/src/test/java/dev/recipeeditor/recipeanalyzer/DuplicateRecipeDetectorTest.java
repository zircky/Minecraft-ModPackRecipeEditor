package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.recipeanalyzer.conflict.DuplicateRecipeDetector;
import java.util.*;
import org.junit.jupiter.api.Test;

final class DuplicateRecipeDetectorTest {
  private final AnalysisFixtures f = new AnalysisFixtures();

  @Test
  void exactDuplicatesIgnoreObjectFieldOrderButKeepUnknownProperties() {
    final String raw = f.shapeless("test:a", "test:b", 1);
    final var result =
        new DuplicateRecipeDetector()
            .analyze(
                f.context(
                    List.of(
                        f.recipe("test:one", raw),
                        f.recipe(
                            "test:two",
                            "{\"result\":{\"count\":1,\"item\":\"test:b\"},\"ingredients\":[{\"item\":\"test:a\"}],\"type\":\"minecraft:crafting_shapeless\"}"),
                        f.recipe(
                            "test:three",
                            raw.replace("\"count\":1", "\"count\":1,\"custom\":true")))));
    assertEquals(1, result.metrics().get("exactGroups"));
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(d -> d.code().equals("DUPLICATE_RECIPE") && d.affectedRecipes().size() == 2));
  }

  @Test
  void sameIdsAndDifferentOutputsAreSourceConflicts() {
    final var result =
        new DuplicateRecipeDetector()
            .analyze(
                f.context(
                    List.of(
                        f.recipe("test:same", "test:a", "test:b"),
                        f.recipe("test:same", "test:a", "test:c"))));
    assertEquals(1, result.metrics().get("sameIdGroups"));
    assertEquals(0, result.metrics().get("exactGroups"));
  }

  @Test
  void sameOutputAloneIsNotADuplicate() {
    final var result =
        new DuplicateRecipeDetector()
            .analyze(
                f.context(
                    List.of(
                        f.recipe("test:one", "test:a", "test:c"),
                        f.recipe("test:two", "test:b", "test:c"))));
    assertEquals(0, result.metrics().get("exactGroups"));
    assertEquals(0, result.metrics().get("similarGroups"));
    assertEquals(1, result.metrics().get("sharedOutputGroups"));
  }

  @Test
  void proportionalBatchesAreOnlyPotentiallyEquivalent() {
    final String first = f.shapeless("test:a", "test:b", 1);
    final String second =
        first
            .replace("[{\"item\":\"test:a\"}]", "[{\"item\":\"test:a\"},{\"item\":\"test:a\"}]")
            .replace("\"count\":1", "\"count\":2");
    final var result =
        new DuplicateRecipeDetector()
            .analyze(f.context(List.of(f.recipe("test:one", first), f.recipe("test:two", second))));
    assertEquals(1, result.metrics().get("equivalentRatioGroups"));
    assertEquals(0, result.metrics().get("exactGroups"));
  }
}
