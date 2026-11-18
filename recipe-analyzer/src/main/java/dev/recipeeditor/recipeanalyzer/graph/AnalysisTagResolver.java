package dev.recipeeditor.recipeanalyzer.graph;

import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeanalyzer.api.AnalysisContext;
import dev.recipeeditor.registry.ResourceKind;
import java.util.*;

/** Static members remain candidates; a missing static tag never proves unavailability. */
public final class AnalysisTagResolver {
  private final AnalysisContext context;
  private final Set<NodeKey> defined = new HashSet<>();
  private final Map<NodeKey, List<String>> runtime = new HashMap<>();
  private final Map<NodeKey, TagResolution> cache = new HashMap<>();

  public AnalysisTagResolver(final AnalysisContext context) {
    this.context = context;
    if (context.usesSnapshot())
      for (final var tag : context.runtimeSnapshot().tags()) {
        final var key =
            new NodeKey(
                DependencyNodeType.TAG,
                tag.kind() == RuntimeData.Kind.FLUID
                    ? "fluid"
                    : tag.kind() == RuntimeData.Kind.BLOCK ? "block" : "item",
                tag.id());
        defined.add(key);
        runtime.put(key, tag.entries());
      }
    else
      context
          .originals()
          .tagIndex()
          .definitions()
          .forEach(
              tag ->
                  defined.add(
                      new NodeKey(
                          DependencyNodeType.TAG,
                          tag.kind() == ResourceKind.FLUID
                              ? "fluid"
                              : tag.kind() == ResourceKind.BLOCK ? "block" : "item",
                          tag.id().toString())));
  }

  public TagResolution resolve(final NodeKey tag) {
    return cache.computeIfAbsent(tag, this::load);
  }

  private TagResolution load(final NodeKey tag) {
    final boolean actual = context.usesSnapshot();
    final var keys = new ArrayList<String>();
    if (actual) keys.addAll(runtime.getOrDefault(tag, List.of()));
    else {
      final ResourceKind kind =
          switch (tag.domain()) {
            case "item" -> ResourceKind.ITEM;
            case "fluid" -> ResourceKind.FLUID;
            default -> null;
          };
      if (kind != null)
        context
            .originals()
            .tagIndex()
            .members(kind, ResourceLocation.parse(tag.id()))
            .forEach(id -> keys.add(id.toString()));
    }
    Collections.sort(keys);
    final boolean truncated = keys.size() > context.settings().maxTagMembers();
    final var members = new ArrayList<NodeKey>();
    final DependencyNodeType type =
        tag.domain().equals("item")
            ? DependencyNodeType.ITEM
            : tag.domain().equals("fluid") ? DependencyNodeType.FLUID : DependencyNodeType.UNKNOWN;
    for (int i = 0; i < Math.min(keys.size(), context.settings().maxTagMembers()); i++)
      members.add(
          new NodeKey(
              type,
              type == DependencyNodeType.ITEM
                  ? "minecraft-item"
                  : type == DependencyNodeType.FLUID ? "minecraft-fluid" : tag.domain(),
              keys.get(i)));
    final TagResolution.Status status =
        !defined.contains(tag)
            ? actual ? TagResolution.Status.MISSING_RUNTIME : TagResolution.Status.MISSING_STATIC
            : keys.isEmpty()
                ? actual ? TagResolution.Status.EMPTY_RUNTIME : TagResolution.Status.EMPTY_STATIC
                : actual
                    ? TagResolution.Status.RESOLVED_RUNTIME
                    : TagResolution.Status.INFERRED_STATIC;
    return new TagResolution(tag, members, status, truncated);
  }
}
