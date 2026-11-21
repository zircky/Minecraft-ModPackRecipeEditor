package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

/** Declared brew registry reference, not an ItemStack or a resolved runtime effect definition. */
public record BrewDefinition(ResourceLocation id,String translationKey,Map<String,String> localizedNames) {
    public BrewDefinition { Objects.requireNonNull(id);Objects.requireNonNull(translationKey);localizedNames=Map.copyOf(localizedNames); }
    public String displayName(final String language){return localizedNames.getOrDefault(language,localizedNames.getOrDefault("en_us",id.toString()));}
}
