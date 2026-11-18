package dev.recipeeditor.recipeanalyzer.api;

import java.util.*;

/** Independent passes share a captured context/dataset; the registry performs no UI or file IO. */
public final class AnalyzerRegistry {
  private final Map<String, RecipeAnalyzer> analyzers = new LinkedHashMap<>();

  public void register(final RecipeAnalyzer analyzer) {
    Objects.requireNonNull(analyzer);
    if (analyzer.id().isBlank() || analyzers.putIfAbsent(analyzer.id(), analyzer) != null)
      throw new IllegalArgumentException("Duplicate/invalid analyzer ID: " + analyzer.id());
  }

  public List<String> ids() {
    return List.copyOf(analyzers.keySet());
  }

  public List<AnalysisResult> analyze(final AnalysisContext context, final Set<String> selected) {
    if (!analyzers.keySet().containsAll(selected))
      throw new IllegalArgumentException("Unknown analyzer selection");
    final var results = new ArrayList<AnalysisResult>();
    for (final var entry : analyzers.entrySet())
      if (selected.contains(entry.getKey())) {
        context.checkCancelled();
        results.add(entry.getValue().analyze(context));
      }
    return List.copyOf(results);
  }
}
