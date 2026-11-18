package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.RecipeDefinition;
import java.time.Instant;
import java.util.*;
/** DELETE uses an empty replacement list; CREATE/UPDATE have exactly one immutable definition. */
public record RecipeChange(ResourceLocation id,RecipeChangeType type,List<OriginalRecipeReference> originals,
        List<RecipeDefinition> replacement,Instant timestamp,Map<String,String> metadata) {
    public RecipeChange {
        Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(timestamp);
        originals=List.copyOf(originals); replacement=List.copyOf(replacement); metadata=Map.copyOf(metadata);
        if(type==RecipeChangeType.DELETE?!replacement.isEmpty():replacement.size()!=1) throw new IllegalArgumentException("Invalid change replacement");
        if(!replacement.isEmpty() && !replacement.getFirst().id().equals(id)) throw new IllegalArgumentException("Recipe ID mismatch");
        if(type==RecipeChangeType.CREATE?!originals.isEmpty():originals.isEmpty()) throw new IllegalArgumentException("Invalid base references");
    }
}
