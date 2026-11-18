package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import java.util.Optional;
public record RecipeTypeDefinition(String rawId) {
    public RecipeTypeDefinition { java.util.Objects.requireNonNull(rawId); }
    public String displayId() { return location().map(ResourceLocation::toString).orElse(rawId); }
    public Optional<ResourceLocation> location() {
        try { return Optional.of(ResourceLocation.parse(rawId)); } catch (IllegalArgumentException error) { return Optional.empty(); }
    }
}
