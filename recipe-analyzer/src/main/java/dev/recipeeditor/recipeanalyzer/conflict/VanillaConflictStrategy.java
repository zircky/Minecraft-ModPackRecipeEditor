package dev.recipeeditor.recipeanalyzer.conflict;

import dev.recipeeditor.recipeanalyzer.model.RecipeFacts;
import dev.recipeeditor.recipeengine.RecipeTypeDefinition;
import java.util.*;

public final class VanillaConflictStrategy implements RecipeConflictStrategy {
  private final Set<String> supported =
      Set.of(
          "minecraft:crafting_shaped",
          "minecraft:crafting_shapeless",
          "minecraft:smelting",
          "minecraft:blasting",
          "minecraft:smoking",
          "minecraft:campfire_cooking",
          "minecraft:stonecutting");

  @Override
  public boolean supports(final RecipeTypeDefinition type) {
    return supported.contains(type.displayId());
  }

  @Override
  public Assessment assess(final List<RecipeFacts> recipes) {
    return new Assessment(
        recipes.stream().allMatch(RecipeFacts::confirmedMatcher),
        "Known Vanilla matcher predicates overlap; selection/load order is not inferred and overlap"
            + " need not be an error");
  }
}
