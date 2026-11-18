package dev.recipeeditor.recipeanalyzer.api;

public interface RecipeAnalyzer {
  String id();

  AnalysisResult analyze(AnalysisContext context);
}
