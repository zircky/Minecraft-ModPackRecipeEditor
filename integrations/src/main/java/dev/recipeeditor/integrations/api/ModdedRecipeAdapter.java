package dev.recipeeditor.integrations.api;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.util.Map;

/** Reuses RecipeParser for parsing/validation; rendering and export writing remain separate. */
public interface ModdedRecipeAdapter extends RecipeParser {
    Map<ResourceLocation,ModdedRecipeType> types();
    RecipeMachineLayout layout(RecipeDefinition recipe);
    RecipeTypeCapabilities capabilities(RecipeDefinition recipe);
    @Override default boolean supports(final ResourceLocation type){return types().containsKey(type);}
}
