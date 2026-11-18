package dev.recipeeditor.recipeanalyzer.api;

import dev.recipeeditor.recipeanalyzer.conflict.*;
import dev.recipeeditor.recipeanalyzer.graph.RecipeCycleDetector;

public final class DefaultAnalyzers {
  public AnalyzerRegistry registry() {
    final var result = new AnalyzerRegistry();
    result.register(new DuplicateRecipeDetector());
    result.register(new RecipeConflictAnalyzer());
    result.register(new RecipeCycleDetector());
    return result;
  }
}
