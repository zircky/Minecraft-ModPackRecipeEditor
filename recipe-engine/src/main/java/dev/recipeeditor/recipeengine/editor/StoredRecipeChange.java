package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.RecipeSource;
import java.time.Instant;
import java.util.*;
public record StoredRecipeChange(ResourceLocation id,RecipeChangeType type,List<OriginalRecipeReference> originals,
        List<String> replacementJson,Instant timestamp,Map<String,String> metadata,List<RecipeSource> replacementSources) {
    public StoredRecipeChange(final ResourceLocation id,final RecipeChangeType type,final List<OriginalRecipeReference> originals,
            final List<String> replacementJson,final Instant timestamp,final Map<String,String> metadata) {
        this(id,type,originals,replacementJson,timestamp,metadata,List.of());
    }
    public StoredRecipeChange { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(type); java.util.Objects.requireNonNull(timestamp);
        originals=List.copyOf(originals); replacementJson=List.copyOf(replacementJson); metadata=Map.copyOf(metadata);replacementSources=List.copyOf(replacementSources);
        if(!replacementSources.isEmpty()&&replacementSources.size()!=replacementJson.size())throw new IllegalArgumentException("Replacement provenance count mismatch");
        if(type==RecipeChangeType.DELETE?!replacementJson.isEmpty():replacementJson.size()!=1) throw new IllegalArgumentException("Invalid replacement count");
        if(type==RecipeChangeType.CREATE?!originals.isEmpty():originals.isEmpty()) throw new IllegalArgumentException("Invalid base references"); }
}
