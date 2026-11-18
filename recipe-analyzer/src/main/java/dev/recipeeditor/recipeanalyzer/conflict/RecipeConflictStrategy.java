package dev.recipeeditor.recipeanalyzer.conflict;

import dev.recipeeditor.recipeanalyzer.model.RecipeFacts;
import dev.recipeeditor.recipeengine.RecipeTypeDefinition;
import java.util.List;

public interface RecipeConflictStrategy {
  boolean supports(RecipeTypeDefinition type);

  Assessment assess(List<RecipeFacts> recipes);

  record Assessment(boolean confirmedOverlap, String evidence) {}
}
