package dev.recipeeditor.recipeanalyzer.api;

import java.util.*;

public record AnalysisDiagnostic(
    String code,
    Severity severity,
    String message,
    List<String> affectedRecipes,
    List<String> affectedResources,
    List<String> evidence,
    String suggestedAction) {
  public enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  public AnalysisDiagnostic {
    Objects.requireNonNull(code);
    Objects.requireNonNull(severity);
    Objects.requireNonNull(message);
    Objects.requireNonNull(suggestedAction);
    affectedRecipes = List.copyOf(affectedRecipes);
    affectedResources = List.copyOf(affectedResources);
    evidence = List.copyOf(evidence);
  }
}
