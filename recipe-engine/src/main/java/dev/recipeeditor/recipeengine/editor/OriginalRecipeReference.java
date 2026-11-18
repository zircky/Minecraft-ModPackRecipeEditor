package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.recipeengine.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
public record OriginalRecipeReference(RecipeSource source,String contentHash) {
    public static OriginalRecipeReference from(final RecipeDefinition recipe) {
        try { return new OriginalRecipeReference(recipe.source(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(recipe.rawJson().getBytes(StandardCharsets.UTF_8)))); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
