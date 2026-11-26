package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.Objects;
import java.util.regex.Pattern;

/** Location of recipe documents. This describes discovery, not JSON semantics. */
public record ResourceLayout(String recipeRoot, String recipesDirectory, boolean includeVanillaRecipes) {
    private static final Pattern RECIPE_FILE = Pattern.compile("[a-z0-9_.-]+/[a-z0-9/._-]+\\.json");

    public ResourceLayout {
        Objects.requireNonNull(recipeRoot);
        Objects.requireNonNull(recipesDirectory);
        if (!(recipeRoot.isEmpty() && recipesDirectory.isEmpty())
                && (!recipeRoot.matches("assets|data") || !recipesDirectory.matches("[a-z][a-z0-9_]*"))) {
            throw new IllegalArgumentException("Invalid recipe layout");
        }
    }

    public ResourceLayout(final String recipeRoot, final String recipesDirectory) {
        this(recipeRoot, recipesDirectory, true);
    }

    public static ResourceLayout minecraft1201() { return new ResourceLayout("data", "recipes", false); }
    public static ResourceLayout noRecipeFiles() { return new ResourceLayout("", "", false); }
    public boolean usesDatapacks() { return recipeRoot.equals("data"); }

    public boolean isRecipe(final String path) {
        if (recipeRoot.isEmpty() || !path.startsWith(recipeRoot + "/")) return false;
        final int separator = path.indexOf('/', recipeRoot.length() + 1);
        if (separator < 0 || !path.startsWith("/" + recipesDirectory + "/", separator)) return false;
        return RECIPE_FILE.matcher(path.substring(recipeRoot.length() + 1, separator + 1)
                + path.substring(separator + recipesDirectory.length() + 2)).matches();
    }

    public String recipePath(final ResourceLocation id) {
        if (recipeRoot.isEmpty()) throw new IllegalStateException("This layout has no recipe files");
        return recipeRoot + "/" + id.namespace() + "/" + recipesDirectory + "/" + id.path() + ".json";
    }
}
