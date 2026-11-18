package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
public sealed interface IngredientDefinition {
    record Item(ResourceLocation id) implements IngredientDefinition { public Item { Objects.requireNonNull(id); } }
    record Tag(ResourceLocation id) implements IngredientDefinition { public Tag { Objects.requireNonNull(id); } }
    record Alternatives(List<IngredientDefinition> choices) implements IngredientDefinition { public Alternatives { choices=List.copyOf(choices); } }
    record Fluid(ResourceLocation id, boolean tag, long amount, String rawJson) implements IngredientDefinition {
        public Fluid { Objects.requireNonNull(id); if (amount < 1) throw new IllegalArgumentException("Positive fluid amount required"); }
    }
    record Sized(IngredientDefinition ingredient, long amount, String rawJson) implements IngredientDefinition {
        public Sized { Objects.requireNonNull(ingredient); if (amount < 1) throw new IllegalArgumentException("Positive ingredient amount required"); }
    }
    record Custom(String type, String rawJson) implements IngredientDefinition { }
    record Unknown(String rawJson) implements IngredientDefinition { }
}
