package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.integrations.gregtech.*;
import dev.recipeeditor.recipeanalyzer.graph.DependencyEdge.*;
import dev.recipeeditor.recipeengine.*;
import java.util.*;

/** Registered only for the context's verified GTCEu 7.1.4 capability. Reuses the Phase 7 mapper. */
public final class GregTechDependencyAdapter implements RecipeDependencyAdapter {
  @Override
  public boolean supports(final RecipeDefinition recipe) {
    return recipe.type().displayId().startsWith("gtceu:")
        && recipe.status() != RecipeParseStatus.INVALID;
  }

  @Override
  public ListPorts describe(final RecipeDefinition recipe) {
    final var model = new GregTechRecipeMapper().map(recipe);
    final var inputs = new ArrayList<RecipePort>();
    final var outputs = new ArrayList<RecipePort>();
    final var ingredients = new IngredientDependencies();
    int slot = 0;
    for (final var content : model.contents()) {
      if (content.capability().equals("eu"))
        continue; // Energy is not an item/fluid dependency; cost calculation is Step 4.
      final boolean input = content.role().equals("inputs") || content.role().equals("tickInputs");
      final Consumption consumption =
          !input
              ? Consumption.NOT_APPLICABLE
              : content.nonConsumable()
                  ? Consumption.CATALYST
                  : content.chance().isPresent()
                          && content.maxChance().isPresent()
                          && content.chance().getAsInt() == content.maxChance().getAsInt()
                      ? Consumption.CONSUMED
                      : Consumption.UNKNOWN;
      final var port =
          new RecipePort(
              content.role() + "/" + content.capability() + "/" + slot++,
              ingredients.describe(content.ingredient(), true),
              consumption,
              content.role().startsWith("tick") ? Cadence.PER_TICK : Cadence.PER_RECIPE,
              content.chance().isPresent() ? content.chance().getAsInt() : null,
              content.maxChance().isPresent() ? content.maxChance().getAsInt() : null,
              content.tierChanceBoost().isPresent() ? content.tierChanceBoost().getAsInt() : null);
      (input ? inputs : outputs).add(port);
    }
    final var warnings = new ArrayList<String>();
    if (!model.conditions().isEmpty())
      warnings.add("GT recipe conditions are retained but not evaluated");
    if (model.additionalProperties().entrySet().stream()
        .anyMatch(e -> !e.getKey().equals("data") || !e.getValue().equals("{}")))
      warnings.add("Additional GT properties may affect dependencies/matching");
    if (inputs.stream().anyMatch(p -> !ingredients.known(p.ingredient()))
        || outputs.stream().anyMatch(p -> !ingredients.known(p.ingredient())))
      warnings.add("Custom GT capability/ingredient has no dependency adapter");
    return new ListPorts(inputs, outputs, warnings.isEmpty(), warnings);
  }
}
