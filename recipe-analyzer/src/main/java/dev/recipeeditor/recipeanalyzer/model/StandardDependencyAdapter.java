package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeanalyzer.graph.DependencyEdge.*;
import dev.recipeeditor.recipeengine.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Only the existing fully parsed Vanilla contract implies deterministic consumption/output
 * quantities.
 */
public final class StandardDependencyAdapter implements RecipeDependencyAdapter {
  private final IngredientDependencies dependencies = new IngredientDependencies();

  @Override
  public boolean supports(final RecipeDefinition recipe) {
    return true;
  }

  @Override
  public ListPorts describe(final RecipeDefinition recipe) {
    final var ingredients = dependencies;
    final var inputs = new ArrayList<RecipePort>();
    final var outputs = new ArrayList<RecipePort>();
    final boolean known =
        recipe.status() == RecipeParseStatus.PARSED
            && recipe.type().displayId().startsWith("minecraft:");
    int slot = 0;
    for (final var input : recipe.inputs())
      inputs.add(
          new RecipePort(
              "input/" + slot++,
              ingredients.describe(input, known),
              known ? Consumption.CONSUMED : Consumption.UNKNOWN,
              Cadence.PER_RECIPE,
              null,
              null,
              null));
    slot = 0;
    for (final var output : recipe.outputs()) {
      final DependencyIngredient ingredient;
      if (output instanceof RecipeOutput.Fixed fixed)
        ingredient =
            new DependencyIngredient(
                new NodeKey(DependencyNodeType.ITEM, "minecraft-item", fixed.id().toString()),
                BigDecimal.valueOf(fixed.count()),
                List.of(),
                fixed.copiesBaseData() ? new CanonicalJson().hash(fixed.rawDetails()) : "");
      else if (output instanceof RecipeOutput.Capability capability)
        ingredient = ingredients.describe(capability.ingredient(), false);
      else ingredient = ingredients.opaque(((RecipeOutput.Dynamic) output).description());
      outputs.add(
          new RecipePort(
              "output/" + slot++,
              ingredient,
              Consumption.NOT_APPLICABLE,
              Cadence.PER_RECIPE,
              known ? 1 : null,
              known ? 1 : null,
              null));
    }
    final boolean complete =
        known
            && inputs.stream().allMatch(p -> ingredients.known(p.ingredient()))
            && outputs.stream().allMatch(p -> ingredients.known(p.ingredient()));
    return new ListPorts(
        inputs,
        outputs,
        complete,
        complete
            ? List.of()
            : List.of(
                "Only declared dependencies are known; custom matching, outputs or consumption may"
                    + " be incomplete"));
  }
}
