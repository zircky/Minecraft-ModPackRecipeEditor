package dev.recipeeditor.recipeanalyzer.graph;

import dev.recipeeditor.recipeanalyzer.api.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Iterative Kosaraju: O(V+E), uses the existing forward/reverse indexes, no JVM recursion depth
 * limit.
 */
public final class RecipeCycleDetector implements RecipeAnalyzer {
  @Override
  public String id() {
    return "cycles";
  }

  private record Frame(NodeKey node, Iterator<DependencyEdge> edges) {}

  public List<Set<NodeKey>> stronglyConnectedComponents(
      final RecipeDependencyGraph graph, final BooleanSupplier cancelled) {
    final var seen = new HashSet<NodeKey>();
    final var order = new ArrayList<NodeKey>();
    final var stack = new ArrayDeque<Frame>();
    for (final var root : graph.nodes().keySet()) {
      check(cancelled);
      if (!seen.add(root)) continue;
      stack.push(new Frame(root, graph.outgoing(root).iterator()));
      while (!stack.isEmpty()) {
        check(cancelled);
        final var frame = stack.peek();
        if (!frame.edges().hasNext()) {
          order.add(frame.node());
          stack.pop();
          continue;
        }
        final var edge = frame.edges().next();
        if (edge.role() == DependencyEdge.Role.MACHINE_REQUIREMENT) continue;
        if (seen.add(edge.target()))
          stack.push(new Frame(edge.target(), graph.outgoing(edge.target()).iterator()));
      }
    }
    seen.clear();
    final var result = new ArrayList<Set<NodeKey>>();
    final var pending = new ArrayDeque<NodeKey>();
    for (int i = order.size() - 1; i >= 0; i--) {
      final var root = order.get(i);
      if (!seen.add(root)) continue;
      final var members = new LinkedHashSet<NodeKey>();
      pending.push(root);
      while (!pending.isEmpty()) {
        check(cancelled);
        final var node = pending.pop();
        members.add(node);
        for (final var edge : graph.incoming(node))
          if (edge.role() != DependencyEdge.Role.MACHINE_REQUIREMENT && seen.add(edge.source()))
            pending.push(edge.source());
      }
      result.add(Collections.unmodifiableSet(members));
    }
    return List.copyOf(result);
  }

  @Override
  public AnalysisResult analyze(final AnalysisContext context) {
    final var graph = context.dependencyGraph();
    final var out = new DiagnosticCollector(context.settings().maxDiagnostics());
    graph.diagnostics().forEach(out::add);
    final var components =
        stronglyConnectedComponents(
            graph,
            () -> {
              context.checkCancelled();
              return false;
            });
    long cycles = 0, gain = 0, deadlock = 0;
    for (final var component : components) {
      context.checkCancelled();
      if (component.size() == 1
          && graph.outgoing(component.iterator().next()).stream()
              .noneMatch(
                  e ->
                      e.source().equals(e.target())
                          && e.role() != DependencyEdge.Role.MACHINE_REQUIREMENT)) continue;
      cycles++;
      final boolean amplification = amplification(component, graph);
      final boolean closed = !amplification && closedConsumed(component, graph);
      if (amplification) gain++;
      if (closed) deadlock++;
      final String classification =
          amplification
              ? "POTENTIAL_RESOURCE_LOOP"
              : closed ? "POTENTIAL_DEPENDENCY_DEADLOCK" : "INFORMATIONAL_CYCLE";
      out.add(
          new AnalysisDiagnostic(
              "DEPENDENCY_CYCLE",
              amplification || closed
                  ? AnalysisDiagnostic.Severity.WARNING
                  : AnalysisDiagnostic.Severity.INFO,
              classification + ": a cycle alone is not an error or proof of unavailability",
              component.stream()
                  .filter(n -> n.type() == DependencyNodeType.RECIPE)
                  .limit(context.settings().maxRelated())
                  .map(n -> graph.nodes().get(n).label())
                  .toList(),
              component.stream()
                  .filter(
                      n ->
                          n.type() != DependencyNodeType.RECIPE
                              && n.type() != DependencyNodeType.MACHINE)
                  .limit(context.settings().maxRelated())
                  .map(NodeKey::toString)
                  .toList(),
              List.of(
                  "SCC size: " + component.size(),
                  amplification
                      ? "A deterministic single-resource conversion increases its declared"
                          + " quantity; energy, conditions and external limits are not evaluated"
                      : closed
                          ? "No modeled external entry; an external seed/world/loot source may"
                              + " still resolve the cycle"
                          : "Catalyst/recycling/alternative or external dependencies can be"
                              + " legitimate"),
              "Inspect the SCC and external sources before treating the cycle as a problem"));
    }
    return context.result(
        id(),
        !graph.truncated() && !out.truncated(),
        Map.of(
            "components",
            (long) components.size(),
            "cycles",
            cycles,
            "potentialResourceLoops",
            gain,
            "potentialDeadlocks",
            deadlock,
            "nodes",
            (long) graph.nodes().size(),
            "edges",
            (long) graph.edges().size()),
        out.values());
  }

  private boolean amplification(final Set<NodeKey> component, final RecipeDependencyGraph graph) {
    for (final var node : component)
      if (node.type() == DependencyNodeType.RECIPE) {
        final var inputs =
            graph.incoming(node).stream()
                .filter(e -> e.role() == DependencyEdge.Role.INPUT)
                .toList();
        if (inputs.isEmpty()) continue;
        final var resource = inputs.getFirst().source();
        if (resource.type() != DependencyNodeType.ITEM
            && resource.type() != DependencyNodeType.FLUID) continue;
        if (inputs.stream()
            .anyMatch(
                e ->
                    !e.source().equals(resource)
                        || e.quantity() == null
                        || e.consumption() != DependencyEdge.Consumption.CONSUMED
                        || e.cadence() != DependencyEdge.Cadence.PER_RECIPE
                        || !e.representationHash().isEmpty())) continue;
        BigDecimal consumed = BigDecimal.ZERO, produced = BigDecimal.ZERO;
        for (final var input : inputs) consumed = consumed.add(input.quantity());
        for (final var output : graph.outgoing(node))
          if (output.target().equals(resource)
              && output.guaranteedOutput()
              && output.quantity() != null
              && output.cadence() == DependencyEdge.Cadence.PER_RECIPE
              && output.representationHash().isEmpty()) produced = produced.add(output.quantity());
        if (produced.compareTo(consumed) > 0) return true;
      }
    return false;
  }

  private boolean closedConsumed(final Set<NodeKey> component, final RecipeDependencyGraph graph) {
    boolean consumed = false;
    for (final var node : component) {
      if (node.type() == DependencyNodeType.UNKNOWN
          || node.type() == DependencyNodeType.CHOICE
          || node.type() == DependencyNodeType.TAG) return false;
      for (final var edge : graph.incoming(node)) {
        if (edge.role() == DependencyEdge.Role.MACHINE_REQUIREMENT) continue;
        if (!component.contains(edge.source())) return false;
        if (edge.role() == DependencyEdge.Role.INPUT) {
          if (edge.consumption() != DependencyEdge.Consumption.CONSUMED) return false;
          consumed = true;
        }
      }
    }
    return consumed;
  }

  private void check(final BooleanSupplier cancelled) {
    if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
      throw new java.util.concurrent.CancellationException("Cycle detection cancelled");
  }
}
