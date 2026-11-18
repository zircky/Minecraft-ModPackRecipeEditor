package dev.recipeeditor.recipeanalyzer.graph;

import dev.recipeeditor.recipeanalyzer.api.*;
import java.util.*;

/**
 * Ref-counted incremental adjacency index. Only changed fragments are unindexed/indexed; publish is
 * atomic.
 */
public final class RecipeGraphIndex {
  private final Map<String, GraphFragment> fragments = new LinkedHashMap<>();
  private final Map<NodeKey, DependencyNode> nodes = new LinkedHashMap<>();
  private final Map<NodeKey, Integer> nodeRefs = new HashMap<>();
  private final Map<DependencyEdge, Integer> edgeRefs = new LinkedHashMap<>();
  private final Map<NodeKey, Set<DependencyEdge>> outgoing = new HashMap<>(),
      incoming = new HashMap<>();

  public synchronized GraphFragment fragment(final String key) {
    return fragments.get(key);
  }

  public synchronized RecipeDependencyGraph publish(
      final List<GraphFragment> proposed,
      final AnalysisSettings settings,
      final boolean upstreamTruncated,
      final Runnable checkCancelled,
      final List<AnalysisDiagnostic> diagnostics) {
    final var accepted = new LinkedHashMap<String, GraphFragment>();
    final var wantedNodes = new HashSet<NodeKey>();
    final var wantedEdges = new HashSet<DependencyEdge>();
    int skipped = 0;
    for (final var fragment : proposed) {
      checkCancelled.run();
      final var newNodes = new ArrayList<NodeKey>();
      final var newEdges = new ArrayList<DependencyEdge>();
      for (final var node : fragment.nodes().keySet())
        if (!wantedNodes.contains(node)) newNodes.add(node);
      for (final var edge : fragment.edges()) if (!wantedEdges.contains(edge)) newEdges.add(edge);
      if ((long) wantedNodes.size() + newNodes.size() > settings.maxNodes()
          || (long) wantedEdges.size() + newEdges.size() > settings.maxEdges()) {
        skipped++;
        continue;
      }
      if (accepted.putIfAbsent(fragment.key(), fragment) != null)
        throw new IllegalArgumentException("Duplicate graph fragment");
      wantedNodes.addAll(newNodes);
      wantedEdges.addAll(newEdges);
    }
    checkCancelled.run();
    int removed = 0, added = 0, reused = 0;
    // No cancellation point inside commit: readers see either the old or the complete new index.
    for (final String key : List.copyOf(fragments.keySet())) {
      final var old = fragments.get(key);
      final var next = accepted.get(key);
      if (next == null || !next.fingerprint().equals(old.fingerprint())) {
        unindex(old);
        fragments.remove(key);
        removed++;
      }
    }
    for (final var fragment : accepted.values())
      if (fragments.containsKey(fragment.key())) reused++;
      else {
        index(fragment);
        fragments.put(fragment.key(), fragment);
        added++;
      }
    final var notices = new DiagnosticCollector(settings.maxDiagnostics());
    diagnostics.forEach(notices::add);
    if (skipped > 0)
      notices.add(
          new AnalysisDiagnostic(
              "GRAPH_LIMIT",
              AnalysisDiagnostic.Severity.WARNING,
              "Graph budget reached; some contributions were omitted",
              List.of(),
              List.of(),
              List.of("Skipped fragments: " + skipped),
              "Narrow the input or increase the explicit graph budget"));
    return new RecipeDependencyGraph(
        nodes,
        edgeRefs.keySet(),
        outgoing,
        incoming,
        notices.values(),
        skipped > 0
            || upstreamTruncated
            || diagnostics.stream()
                .anyMatch(
                    d ->
                        d.code().equals("TAG_EXPANSION_LIMIT")
                            || d.code().equals("ANALYSIS_LIMIT")
                            || d.code().equals("GRAPH_PREPARATION_LIMIT")),
        accepted.values().stream().anyMatch(GraphFragment::unknown),
        added,
        removed,
        reused);
  }

  private void index(final GraphFragment fragment) {
    fragment
        .nodes()
        .forEach(
            (key, value) -> {
              nodeRefs.merge(key, 1, Integer::sum);
              nodes.putIfAbsent(key, value);
            });
    for (final var edge : fragment.edges()) {
      if (edgeRefs.merge(edge, 1, Integer::sum) == 1) {
        outgoing.computeIfAbsent(edge.source(), k -> new LinkedHashSet<>()).add(edge);
        incoming.computeIfAbsent(edge.target(), k -> new LinkedHashSet<>()).add(edge);
      }
    }
  }

  private void unindex(final GraphFragment fragment) {
    for (final var edge : fragment.edges())
      if (edgeRefs.computeIfPresent(edge, (key, count) -> count == 1 ? null : count - 1) == null) {
        remove(outgoing, edge.source(), edge);
        remove(incoming, edge.target(), edge);
      }
    for (final var node : fragment.nodes().keySet())
      if (nodeRefs.computeIfPresent(node, (key, count) -> count == 1 ? null : count - 1) == null)
        nodes.remove(node);
  }

  private void remove(
      final Map<NodeKey, Set<DependencyEdge>> index, final NodeKey key, final DependencyEdge edge) {
    final var values = index.get(key);
    if (values != null) {
      values.remove(edge);
      if (values.isEmpty()) index.remove(key);
    }
  }
}
