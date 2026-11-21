package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import java.util.Objects;

/** Runtime identity is evidence of presence, not evidence of a declaring mod or resource pack. */
public record RuntimeRecipeObservation(ResourceLocation recipeId, String recipeType, String serializer,
        String snapshotId, String collectedAt, String sourceInformation) {
    public RuntimeRecipeObservation {
        Objects.requireNonNull(recipeId);
        Objects.requireNonNull(recipeType);
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(collectedAt);
        Objects.requireNonNull(sourceInformation);
    }
}
