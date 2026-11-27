package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.recipeengine.RecipeSource;
import dev.recipeeditor.registry.SourceType;
import java.nio.file.Path;
import java.util.List;

/** Identity of a serializer projection, independent of the time at which it was captured. */
public final class RuntimeRecipeBase {
    private static final String METHOD = "Verified runtime serializer projection";

    public RecipeSource source(final Path instance, final String adapter, final String type, final String serializer) {
        return new RecipeSource(SourceType.RUNTIME, List.of(), instance.toAbsolutePath().normalize(), "",
                String.join(" | ", METHOD, adapter, type, serializer));
    }

    public boolean matches(final RecipeSource source) {
        return source.type() == SourceType.RUNTIME && source.discoveryMethod().startsWith(METHOD + " | ");
    }
}
