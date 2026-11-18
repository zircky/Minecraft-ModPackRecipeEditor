package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import java.util.List;
public record SourceConflict(ResourceLocation recipeId,String message,RecipeChange savedChange,List<OriginalRecipeReference> currentSources) {
    public SourceConflict { currentSources=List.copyOf(currentSources); }
}
