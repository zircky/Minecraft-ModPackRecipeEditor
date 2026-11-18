package dev.recipeeditor.recipeanalyzer.graph;

import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.graph.DependencyEdge.*;
import dev.recipeeditor.recipeanalyzer.model.*;
import java.util.*;

public final class RecipeGraphBuilder {
  private final CanonicalJson fingerprint = new CanonicalJson();

  public RecipeDependencyGraph build(final AnalysisContext context, final RecipeGraphIndex index) {
    final var fragments = new ArrayList<GraphFragment>();
    final var tags = new LinkedHashSet<NodeKey>();
    final var diagnostics = new DiagnosticCollector(context.settings().maxDiagnostics());
    context.dataset().diagnostics().forEach(diagnostics::add);
    long preparationNodes = 0, preparationEdges = 0;
    for (final var recipe : context.dataset().recipes()) {
      context.checkCancelled();
      final String signature =
          fingerprint.hash(
              recipe.inputs().toString()
                  + recipe.outputs()
                  + recipe.knownDependencies()
                  + recipe.type());
      GraphFragment fragment = index.fragment(recipe.key());
      if (fragment == null || !fragment.fingerprint().equals(signature))
        fragment = recipe(recipe, signature);
      if ((preparationNodes += fragment.nodes().size()) > context.settings().maxNodes()
          || (preparationEdges += fragment.edges().size()) > context.settings().maxEdges()) {
        diagnostics.add(
            new AnalysisDiagnostic(
                "GRAPH_PREPARATION_LIMIT",
                AnalysisDiagnostic.Severity.WARNING,
                "Graph preparation work budget reached",
                List.of(),
                List.of(),
                List.of(),
                "Narrow the input or raise explicit graph budgets"));
        break;
      }
      fragments.add(fragment);
      for (final var node : fragment.nodes().keySet())
        if (node.type() == DependencyNodeType.TAG) tags.add(node);
    }
    final var resolver = new AnalysisTagResolver(context);
    final var json = new CanonicalJson();
    for (final var tag : tags) {
      context.checkCancelled();
      final var resolution = resolver.resolve(tag);
      final String key = "tag:" + tag, hash = json.hash(resolution.toString());
      final var old = index.fragment(key);
      fragments.add(
          old != null && old.fingerprint().equals(hash) ? old : tag(resolution, key, hash));
      if (resolution.status() != TagResolution.Status.RESOLVED_RUNTIME)
        diagnostics.add(
            new AnalysisDiagnostic(
                resolution.members().isEmpty() ? "UNRESOLVED_TAG" : "STATIC_TAG_INFERRED",
                AnalysisDiagnostic.Severity.WARNING,
                "Tag membership evidence: " + resolution.status(),
                List.of(),
                List.of(tag.toString()),
                List.of("Known candidate members: " + resolution.members().size()),
                "Do not select an arbitrary member or infer unavailability from static absence"));
      if (resolution.truncated())
        diagnostics.add(
            new AnalysisDiagnostic(
                "TAG_EXPANSION_LIMIT",
                AnalysisDiagnostic.Severity.WARNING,
                "Tag expansion was bounded",
                List.of(),
                List.of(tag.toString()),
                List.of(),
                "Increase tag budget or inspect tag independently"));
    }
    return index.publish(
        fragments,
        context.settings(),
        context.dataset().truncated(),
        context::checkCancelled,
        diagnostics.values());
  }

  private GraphFragment recipe(final RecipeFacts recipe, final String signature) {
    final var nodes = new LinkedHashMap<NodeKey, DependencyNode>();
    final var edges = new ArrayList<DependencyEdge>();
    final var key = new NodeKey(DependencyNodeType.RECIPE, "recipe-variant", recipe.key());
    nodes.put(key, new DependencyNode(key, recipe.id() + " [" + recipe.origin() + "]"));
    for (final var port : recipe.inputs()) port(recipe, port, key, true, nodes, edges);
    for (final var port : recipe.outputs()) port(recipe, port, key, false, nodes, edges);
    if (recipe.type().startsWith("gtceu:")
        || Set.of(
                "minecraft:smelting",
                "minecraft:blasting",
                "minecraft:smoking",
                "minecraft:campfire_cooking",
                "minecraft:stonecutting")
            .contains(recipe.type())) {
      final var machine =
          new NodeKey(DependencyNodeType.MACHINE, "processor-family", recipe.type());
      nodes.put(
          machine,
          new DependencyNode(
              machine,
              "Processor family " + recipe.type() + " (not a specific installed machine)"));
      edges.add(
          new DependencyEdge(
              machine,
              key,
              recipe.key(),
              "processor",
              Role.MACHINE_REQUIREMENT,
              null,
              Consumption.UNKNOWN,
              Cadence.UNKNOWN,
              null,
              null,
              null,
              ""));
    }
    return new GraphFragment(recipe.key(), signature, nodes, edges, !recipe.knownDependencies());
  }

  private void port(
      final RecipeFacts recipe,
      final RecipePort port,
      final NodeKey target,
      final boolean input,
      final Map<NodeKey, DependencyNode> nodes,
      final List<DependencyEdge> edges) {
    final var operand = operand(recipe.key(), port.slot(), port.ingredient(), nodes, edges, input);
    edges.add(
        new DependencyEdge(
            input ? operand : target,
            input ? target : operand,
            recipe.key(),
            port.slot(),
            input ? Role.INPUT : Role.OUTPUT,
            port.ingredient().quantity(),
            port.consumption(),
            port.cadence(),
            port.chance(),
            port.maxChance(),
            port.boost(),
            port.ingredient().representationHash()));
  }

  private NodeKey operand(
      final String recipe,
      final String slot,
      final DependencyIngredient ingredient,
      final Map<NodeKey, DependencyNode> nodes,
      final List<DependencyEdge> edges,
      final boolean input) {
    if (ingredient.resource() != null) {
      final var key =
          ingredient.resource().type() == DependencyNodeType.UNKNOWN
              ? new NodeKey(
                  DependencyNodeType.UNKNOWN,
                  ingredient.resource().domain(),
                  recipe + "/" + slot + "/" + ingredient.resource().id())
              : ingredient.resource();
      nodes.putIfAbsent(
          key,
          new DependencyNode(
              key,
              key.type() == DependencyNodeType.UNKNOWN
                  ? "Unknown dependency (opaque representation)"
                  : key.type() + " " + key.id()));
      return key;
    }
    final var choice =
        new NodeKey(DependencyNodeType.CHOICE, "ingredient-choice", recipe + "/" + slot);
    nodes.put(choice, new DependencyNode(choice, "Alternative ingredient (choose one)"));
    for (int i = 0; i < ingredient.alternatives().size(); i++) {
      final var alternative = ingredient.alternatives().get(i);
      final var node = operand(recipe, slot + "/" + i, alternative, nodes, edges, input);
      edges.add(
          new DependencyEdge(
              input ? node : choice,
              input ? choice : node,
              recipe,
              slot + "/" + i,
              Role.ALTERNATIVE,
              alternative.quantity(),
              Consumption.NOT_APPLICABLE,
              Cadence.UNKNOWN,
              null,
              null,
              null,
              alternative.representationHash()));
    }
    return choice;
  }

  private GraphFragment tag(final TagResolution resolution, final String key, final String hash) {
    final var nodes = new LinkedHashMap<NodeKey, DependencyNode>();
    final var edges = new ArrayList<DependencyEdge>();
    nodes.put(
        resolution.tag(), new DependencyNode(resolution.tag(), "Tag " + resolution.tag().id()));
    for (final var member : resolution.members()) {
      nodes.put(member, new DependencyNode(member, member.id()));
      edges.add(
          new DependencyEdge(
              member,
              resolution.tag(),
              "",
              "tag-member",
              Role.TAG_MEMBER,
              null,
              Consumption.NOT_APPLICABLE,
              Cadence.UNKNOWN,
              null,
              null,
              null,
              ""));
    }
    final boolean unknown =
        resolution.truncated() || resolution.status() != TagResolution.Status.RESOLVED_RUNTIME;
    return new GraphFragment(key, hash, nodes, edges, unknown);
  }
}
