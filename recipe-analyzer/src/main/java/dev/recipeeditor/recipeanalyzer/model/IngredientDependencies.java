package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeengine.IngredientDefinition;
import java.math.BigDecimal;
import java.util.*;

public final class IngredientDependencies {
  private final CanonicalJson json = new CanonicalJson();

  public DependencyIngredient describe(final IngredientDefinition value, final boolean knownUnit) {
    return describe(value, knownUnit, BigDecimal.ONE);
  }

  private DependencyIngredient describe(
      final IngredientDefinition value, final boolean knownUnit, final BigDecimal factor) {
    if (value instanceof IngredientDefinition.Item item)
      return leaf(
          DependencyNodeType.ITEM,
          "minecraft-item",
          item.id().toString(),
          knownUnit ? factor : null,
          "");
    if (value instanceof IngredientDefinition.Tag tag)
      return leaf(
          DependencyNodeType.TAG, "item", tag.id().toString(), knownUnit ? factor : null, "");
    if (value instanceof IngredientDefinition.Fluid fluid)
      return leaf(
          fluid.tag() ? DependencyNodeType.TAG : DependencyNodeType.FLUID,
          fluid.tag() ? "fluid" : "minecraft-fluid",
          fluid.id().toString(),
          factor.multiply(BigDecimal.valueOf(fluid.amount())),
          json.hash(fluid.rawJson()));
    if (value instanceof IngredientDefinition.Sized sized)
      return describe(
          sized.ingredient(), true, factor.multiply(BigDecimal.valueOf(sized.amount())));
    if (value instanceof IngredientDefinition.Alternatives choices && !choices.choices().isEmpty())
      return new DependencyIngredient(
          null,
          null,
          choices.choices().stream().map(c -> describe(c, knownUnit, factor)).toList(),
          "");
    final String raw =
        value instanceof IngredientDefinition.Custom custom
            ? custom.type() + ":" + custom.rawJson()
            : value instanceof IngredientDefinition.Unknown unknown
                ? unknown.rawJson()
                : value.toString();
    return leaf(DependencyNodeType.UNKNOWN, "ingredient", json.hash(raw), null, json.hash(raw));
  }

  public DependencyIngredient opaque(final String raw) {
    return leaf(DependencyNodeType.UNKNOWN, "output", json.hash(raw), null, json.hash(raw));
  }

  private DependencyIngredient leaf(
      final DependencyNodeType type,
      final String domain,
      final String id,
      final BigDecimal quantity,
      final String hash) {
    return new DependencyIngredient(new NodeKey(type, domain, id), quantity, List.of(), hash);
  }

  public boolean known(final DependencyIngredient value) {
    if (value.resource() != null) return value.resource().type() != DependencyNodeType.UNKNOWN;
    return value.alternatives().stream().allMatch(this::known);
  }
}
