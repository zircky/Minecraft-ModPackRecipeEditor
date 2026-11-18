package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeanalyzer.api.AnalysisDiagnostic;
import java.util.*;

public record AnalysisDataset(
    List<RecipeFacts> recipes, List<AnalysisDiagnostic> diagnostics, boolean truncated) {
  public AnalysisDataset {
    recipes = List.copyOf(recipes);
    diagnostics = List.copyOf(diagnostics);
  }
}
