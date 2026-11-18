package dev.recipeeditor.recipeanalyzer.api;

import java.util.*;

/**
 * Keeps one slot for a visible truncation notice; never claims an exhaustive result after a budget
 * cut.
 */
public final class DiagnosticCollector {
  private final int maximum;
  private final List<AnalysisDiagnostic> values = new ArrayList<>();
  private long omitted;

  public DiagnosticCollector(final int maximum) {
    if (maximum < 2) throw new IllegalArgumentException();
    this.maximum = maximum;
  }

  public void add(final AnalysisDiagnostic value) {
    if (values.size() < maximum - 1) values.add(value);
    else omitted++;
  }

  public boolean truncated() {
    return omitted > 0;
  }

  public List<AnalysisDiagnostic> values() {
    final var result = new ArrayList<>(values);
    if (omitted > 0)
      result.add(
          new AnalysisDiagnostic(
              "ANALYSIS_LIMIT",
              AnalysisDiagnostic.Severity.WARNING,
              "Diagnostic budget exceeded",
              List.of(),
              List.of(),
              List.of("Omitted diagnostics: " + omitted),
              "Narrow the input or raise the explicit budget"));
    return List.copyOf(result);
  }
}
