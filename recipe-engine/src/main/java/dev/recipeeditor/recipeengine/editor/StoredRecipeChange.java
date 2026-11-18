package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import java.time.Instant;
import java.util.*;
public record StoredRecipeChange(ResourceLocation id,RecipeChangeType type,List<OriginalRecipeReference> originals,
        List<String> replacementJson,Instant timestamp,Map<String,String> metadata) {
    public StoredRecipeChange { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(type); java.util.Objects.requireNonNull(timestamp);
        originals=List.copyOf(originals); replacementJson=List.copyOf(replacementJson); metadata=Map.copyOf(metadata);
        if(type==RecipeChangeType.DELETE?!replacementJson.isEmpty():replacementJson.size()!=1) throw new IllegalArgumentException("Invalid replacement count");
        if(type==RecipeChangeType.CREATE?!originals.isEmpty():originals.isEmpty()) throw new IllegalArgumentException("Invalid base references"); }
}
