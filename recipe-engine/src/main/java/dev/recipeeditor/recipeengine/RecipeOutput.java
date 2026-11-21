package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
public sealed interface RecipeOutput {
    /** The declared item ID is fixed; smithing transform also copies base-stack data. */
    record Fixed(ResourceLocation id,int count,boolean copiesBaseData,String rawDetails) implements RecipeOutput {
        public Fixed { if (count<1) throw new IllegalArgumentException("Positive output count required"); }
    }
    /** A mod capability output; quantity/chance remain in its typed integration model and raw representation. */
    record Capability(String capability, IngredientDefinition ingredient, String rawDetails) implements RecipeOutput { }
    record Brew(BrewDefinition definition) implements RecipeOutput { public Brew { java.util.Objects.requireNonNull(definition); } }
    record Dynamic(String description) implements RecipeOutput { }
}
