package dev.recipeeditor.recipeanalyzer.api;

import java.time.Instant;
import java.util.*;

/**
 * complete means the requested pass finished within its budgets, never that a modpack is fully
 * known.
 */
public record AnalysisResult(
    String analyzerId,
    String analyzerVersion,
    Instant timestamp,
    String snapshotId,
    boolean complete,
    Map<String, Long> metrics,
    List<AnalysisDiagnostic> diagnostics) {
  public AnalysisResult {
    metrics = Map.copyOf(metrics);
    diagnostics = List.copyOf(diagnostics);
  }
}
