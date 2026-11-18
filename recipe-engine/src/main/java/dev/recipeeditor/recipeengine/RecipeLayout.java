package dev.recipeeditor.recipeengine;

import java.util.*;
/** Crafting pattern/key or ordered role ingredients, e.g. template/base/addition. */
public record RecipeLayout(List<String> pattern,Map<String,IngredientDefinition> slots) {
    public RecipeLayout { pattern=List.copyOf(pattern); slots=Collections.unmodifiableMap(new LinkedHashMap<>(slots)); }
    public RecipeLayout() { this(List.of(),Map.of()); }
}
