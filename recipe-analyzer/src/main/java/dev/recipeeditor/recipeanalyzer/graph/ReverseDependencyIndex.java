package dev.recipeeditor.recipeanalyzer.graph;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Bounded traversal of resource -> choice/tag -> consumer recipe -> downstream outputs. No
 * cost/obtainability claims.
 */
public final class ReverseDependencyIndex {
  public record Reachability(Set<NodeKey> nodes, Set<NodeKey> recipes, boolean truncated) {
    public Reachability {
      nodes = Set.copyOf(nodes);
      recipes = Set.copyOf(recipes);
    }
  }

  public Reachability dependents(
      final RecipeDependencyGraph graph,
      final NodeKey source,
      final int depth,
      final int maxVisited,
      final BooleanSupplier cancelled) {
    if (depth < 0 || maxVisited < 1) throw new IllegalArgumentException("Invalid traversal limits");
    if (!graph.nodes().containsKey(source))
      return new Reachability(Set.of(), Set.of(), graph.truncated());
    final var seen = new LinkedHashSet<NodeKey>();
    final var recipes = new LinkedHashSet<NodeKey>();
    final var queue = new ArrayDeque<Map.Entry<NodeKey, Integer>>();
    seen.add(source);
    queue.add(Map.entry(source, 0));
    boolean truncated = false;
    while (!queue.isEmpty()) {
      if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
        throw new java.util.concurrent.CancellationException();
      final var current = queue.removeFirst();
      if (current.getValue() >= depth) {
        if (graph.outgoing(current.getKey()).stream()
            .anyMatch(
                e ->
                    e.role() != DependencyEdge.Role.MACHINE_REQUIREMENT
                        && !seen.contains(e.target()))) truncated = true;
        continue;
      }
      for (final var edge : graph.outgoing(current.getKey()))
        if (edge.role() != DependencyEdge.Role.MACHINE_REQUIREMENT
            && !seen.contains(edge.target())) {
          if (seen.size() >= maxVisited) {
            truncated = true;
            continue;
          }
          seen.add(edge.target());
          if (edge.target().type() == DependencyNodeType.RECIPE) recipes.add(edge.target());
          queue.add(Map.entry(edge.target(), current.getValue() + 1));
        }
    }
    return new Reachability(seen, recipes, truncated || graph.truncated());
  }
}
