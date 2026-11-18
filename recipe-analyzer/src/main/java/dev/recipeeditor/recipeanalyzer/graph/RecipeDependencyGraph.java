package dev.recipeeditor.recipeanalyzer.graph;

import dev.recipeeditor.recipeanalyzer.api.AnalysisDiagnostic;
import java.util.*;

/**
 * Immutable structural snapshot. Incoming recipe ports are AND groups; CHOICE/TAG nodes represent
 * OR membership.
 */
public final class RecipeDependencyGraph {
  private final Map<NodeKey, DependencyNode> nodes;
  private final List<DependencyEdge> edges;
  private final Map<NodeKey, List<DependencyEdge>> outgoing, incoming;
  private final List<AnalysisDiagnostic> diagnostics;
  private final boolean truncated, unknown;
  private final int added, removed, reused;

  public RecipeDependencyGraph(
      final Map<NodeKey, DependencyNode> nodes,
      final Collection<DependencyEdge> edges,
      final Map<NodeKey, ? extends Collection<DependencyEdge>> outgoing,
      final Map<NodeKey, ? extends Collection<DependencyEdge>> incoming,
      final List<AnalysisDiagnostic> diagnostics,
      final boolean truncated,
      final boolean unknown,
      final int added,
      final int removed,
      final int reused) {
    this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    this.edges = List.copyOf(edges);
    this.outgoing = freeze(outgoing);
    this.incoming = freeze(incoming);
    this.diagnostics = List.copyOf(diagnostics);
    this.truncated = truncated;
    this.unknown = unknown;
    this.added = added;
    this.removed = removed;
    this.reused = reused;
  }

  private Map<NodeKey, List<DependencyEdge>> freeze(
      final Map<NodeKey, ? extends Collection<DependencyEdge>> values) {
    final var result = new HashMap<NodeKey, List<DependencyEdge>>();
    values.forEach((key, value) -> result.put(key, List.copyOf(value)));
    return Collections.unmodifiableMap(result);
  }

  public Map<NodeKey, DependencyNode> nodes() {
    return nodes;
  }

  public List<DependencyEdge> edges() {
    return edges;
  }

  public List<DependencyEdge> outgoing(final NodeKey key) {
    return outgoing.getOrDefault(key, List.of());
  }

  public List<DependencyEdge> incoming(final NodeKey key) {
    return incoming.getOrDefault(key, List.of());
  }

  public List<AnalysisDiagnostic> diagnostics() {
    return diagnostics;
  }

  public boolean truncated() {
    return truncated;
  }

  public boolean hasUnknownDependencies() {
    return unknown;
  }

  public int addedFragments() {
    return added;
  }

  public int removedFragments() {
    return removed;
  }

  public int reusedFragments() {
    return reused;
  }
}
