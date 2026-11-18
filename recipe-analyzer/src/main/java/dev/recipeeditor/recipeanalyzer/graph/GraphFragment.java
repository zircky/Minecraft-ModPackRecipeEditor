package dev.recipeeditor.recipeanalyzer.graph;

import java.util.*;

/** One recipe or shared tag's adjacency contribution, reusable across analyses. */
public record GraphFragment(
    String key,
    String fingerprint,
    Map<NodeKey, DependencyNode> nodes,
    List<DependencyEdge> edges,
    boolean unknown) {
  public GraphFragment {
    nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    edges = List.copyOf(edges);
  }
}
