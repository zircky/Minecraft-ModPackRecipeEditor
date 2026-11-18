package dev.recipeeditor.recipeengine.editor;

import java.util.*;
public record RecipeEditPreview(RecipeValidationResult validation,boolean visualSupported,String type,Map<String,String> fields,
        List<String> grid,List<String> shapeless,List<RecipeDiffService.Difference> differences,String diffError) {
    public RecipeEditPreview { fields=Map.copyOf(fields); grid=List.copyOf(grid); shapeless=List.copyOf(shapeless); differences=List.copyOf(differences); }
}
