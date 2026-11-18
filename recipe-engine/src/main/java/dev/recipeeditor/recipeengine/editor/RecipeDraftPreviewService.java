package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.util.*;
/** Called by an editor worker; invalid raw text never leaves a stale visual model behind. */
public final class RecipeDraftPreviewService {
    private final RecipeFormService forms=new RecipeFormService();
    private final RecipeDiffService diffs=new RecipeDiffService();
    public RecipeEditPreview inspect(final RecipeValidator validator,final ResourceLocation id,final RecipeSource source,final String baseline,final String raw) {
        final var validation=validator.validate(id,raw,source);
        final boolean visual=forms.visualSupported(raw);
        Map<String,String> fields=Map.of(); List<String> grid=List.of(),shapeless=List.of(); String type="";
        try {
            fields=forms.fields(raw); type=forms.type(raw);
            if(visual) { grid=forms.grid(raw); shapeless=forms.ingredientList(raw); }
        } catch(RuntimeException ignored) { /* Error is already present in the validation result. */ }
        List<RecipeDiffService.Difference> differences=List.of(); String error="";
        try { differences=diffs.compare(baseline,raw); } catch(IllegalArgumentException invalid) { error="Structural diff unavailable while JSON is invalid"; }
        return new RecipeEditPreview(validation,visual,type,fields,grid,shapeless,differences,error);
    }
}
