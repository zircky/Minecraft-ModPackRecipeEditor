package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
/** Immutable project snapshot, separate from unsaved form state. */
public record RecipeChangeSet(Map<ResourceLocation,RecipeChange> changes) {
    public RecipeChangeSet { changes=Collections.unmodifiableMap(new LinkedHashMap<>(changes)); changes.forEach((id,change)-> { if(!id.equals(change.id())) throw new IllegalArgumentException("Change key mismatch"); }); }
    public RecipeChangeSet() { this(Map.of()); }
}
