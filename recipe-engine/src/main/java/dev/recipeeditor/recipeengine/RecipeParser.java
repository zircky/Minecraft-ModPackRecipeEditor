package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
public interface RecipeParser {
    boolean supports(ResourceLocation recipeType);
    RecipeParseResult parse(RecipeParseContext context);
}
