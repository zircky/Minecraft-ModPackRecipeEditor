package dev.recipeeditor.integrations.api;

import java.util.*;

/** Reading, authoring and export are independent, explicitly granted capabilities. */
public record RecipeTypeCapabilities(Set<Feature> features,String reason) {
    public enum Feature { CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_EXPORT, REQUIRES_RUNTIME, REQUIRES_INTEGRATION }
    public RecipeTypeCapabilities {features=Set.copyOf(features);Objects.requireNonNull(reason);}
    public boolean supports(final Feature feature){return features.contains(feature);}
}
