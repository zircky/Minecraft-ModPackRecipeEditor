package dev.recipeeditor.integrations.gregtech;
import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
public record GregTechRecipeDefinition(ResourceLocation id, ResourceLocation recipeType, Optional<String> category,
        OptionalInt duration, OptionalLong inputEUt, OptionalLong outputEUt, List<GregTechContent> contents,
        List<GregTechCondition> conditions, Map<String, String> additionalProperties, String rawJson, boolean runtime) {
    public GregTechRecipeDefinition { contents = List.copyOf(contents); conditions = List.copyOf(conditions); additionalProperties = Map.copyOf(additionalProperties); }
}
