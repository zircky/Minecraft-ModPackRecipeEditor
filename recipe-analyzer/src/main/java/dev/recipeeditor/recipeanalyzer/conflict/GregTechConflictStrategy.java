package dev.recipeeditor.recipeanalyzer.conflict;

import dev.recipeeditor.recipeanalyzer.model.RecipeFacts;
import dev.recipeeditor.recipeengine.RecipeTypeDefinition;
import java.util.*;

/**
 * IO coincidence does not prove a GT lookup conflict: conditions, proxies, machines and lookup
 * lifecycle matter.
 */
public final class GregTechConflictStrategy implements RecipeConflictStrategy {
  @Override
  public boolean supports(final RecipeTypeDefinition type) {
    return type.displayId().startsWith("gtceu:");
  }

  @Override
  public Assessment assess(final List<RecipeFacts> recipes) {
    return new Assessment(
        false,
        "GT IO overlaps are potential only; machine configuration, categories, conditions,"
            + " chance/circuit/custom matching and runtime lookup rules are not proven by this"
            + " pass");
  }
}
