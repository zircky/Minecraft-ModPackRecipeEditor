package dev.recipeeditor.recipeanalyzer.api;

import java.time.Duration;

/** Explicit safety budgets, not promises about modpack performance. */
public record AnalysisSettings(
    int maxRecipes,
    int maxNodes,
    int maxEdges,
    int maxTagMembers,
    int maxDiagnostics,
    int maxRelated,
    int maxComparisons,
    Duration snapshotMaxAge) {
  public AnalysisSettings {
    if (maxRecipes < 1
        || maxNodes < 1
        || maxEdges < 1
        || maxTagMembers < 1
        || maxDiagnostics < 2
        || maxRelated < 2
        || maxComparisons < 1
        || snapshotMaxAge == null
        || snapshotMaxAge.isNegative())
      throw new IllegalArgumentException("Invalid analysis budgets");
  }

  public AnalysisSettings() {
    this(100000, 500000, 1500000, 1024, 5000, 50, 100000, Duration.ofMinutes(30));
  }
}
