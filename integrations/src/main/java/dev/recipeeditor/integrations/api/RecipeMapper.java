package dev.recipeeditor.integrations.api;
import dev.recipeeditor.recipeengine.RecipeDefinition;
public interface RecipeMapper<T> {
    T map(RecipeDefinition recipe);
}
