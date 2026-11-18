package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
public record RecipeParseContext(ResourceLocation id,RecipeTypeDefinition type,RecipeSource source,String rawJson) { }
