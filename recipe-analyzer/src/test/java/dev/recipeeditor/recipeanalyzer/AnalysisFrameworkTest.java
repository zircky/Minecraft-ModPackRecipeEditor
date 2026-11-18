package dev.recipeeditor.recipeanalyzer;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.conflict.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class AnalysisFrameworkTest {
  private final AnalysisFixtures f = new AnalysisFixtures();

  @Test
  void registryRejectsConflictsAndUnknownSelections() {
    final var registry = new DefaultAnalyzers().registry();
    assertEquals(List.of("duplicates", "conflicts", "cycles"), registry.ids());
    assertThrows(
        IllegalArgumentException.class, () -> registry.register(new DuplicateRecipeDetector()));
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.analyze(f.context(List.of()), Set.of("not-registered")));
    assertTrue(registry.analyze(f.context(List.of()), Set.of()).isEmpty());
  }

  @Test
  void effectiveOverlayDoesNotMutateOriginalAndQuarantinesWrongBase() {
    final var original = f.recipe("test:r", "test:a", "test:b");
    final var validator =
        new RecipeValidator(
            new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),
            new RegistryIndex(List.of()));
    final var editor =
        new RecipeEditorService(
            new dev.recipeeditor.recipeengine.RecipeIndex(
                List.of(original), new TagIndex(List.of())),
            validator);
    editor.update(original.id(), f.recipe("test:r", "test:a", "test:c"));
    final var context =
        f.context(
            List.of(original),
            editor.snapshot(),
            new TagIndex(List.of()),
            new RecipeGraphIndex(),
            new AnalysisSettings(),
            List.of());
    assertEquals(1, context.dataset().recipes().size());
    assertTrue(context.dependencyGraph().nodes().containsKey(f.item("test:c")));
    assertFalse(context.dependencyGraph().nodes().containsKey(f.item("test:b")));
    assertEquals(original, context.originals().recipes().getFirst());
    final var changed = f.recipe("test:r", "test:x", "test:y");
    final var conflicting =
        f.context(
            List.of(changed),
            editor.snapshot(),
            new TagIndex(List.of()),
            new RecipeGraphIndex(),
            new AnalysisSettings(),
            List.of());
    assertTrue(
        conflicting.dataset().diagnostics().stream()
            .anyMatch(d -> d.code().equals("PROJECT_CHANGE_CONFLICT")));
    assertTrue(conflicting.dependencyGraph().nodes().containsKey(f.item("test:y")));
  }

  @Test
  void budgetsAreExplicitAndCancellationDoesNotPublishHalfAnIndex() {
    final var recipes =
        List.of(f.recipe("test:a", "test:a", "test:b"), f.recipe("test:b", "test:b", "test:c"));
    final var settings =
        new AnalysisSettings(1, 20, 20, 5, 10, 5, 10, java.time.Duration.ofMinutes(1));
    final var limited =
        f.context(
            recipes,
            new RecipeChangeSet(),
            new TagIndex(List.of()),
            new RecipeGraphIndex(),
            settings,
            List.of());
    assertTrue(limited.dataset().truncated());
    assertFalse(new DuplicateRecipeDetector().analyze(limited).complete());
    final var baseline = f.context(recipes);
    final var cancelled =
        new AnalysisContext(
            baseline.environment(),
            baseline.originals(),
            baseline.registry(),
            null,
            baseline.changes(),
            baseline.capabilities(),
            baseline.settings(),
            List.of(),
            new RecipeGraphIndex(),
            () -> true);
    assertThrows(java.util.concurrent.CancellationException.class, cancelled::dependencyGraph);
  }
}
