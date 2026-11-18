package dev.recipeeditor.recipeengine;

import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.List;
public record RecipeSource(SourceType type,List<String> modIds,Path archivePath,String internalPath,String discoveryMethod) {
    public RecipeSource { modIds=List.copyOf(modIds); }
    public static RecipeSource from(final ResourceSource source) { return new RecipeSource(source.type(),source.modIds(),source.container(),source.internalPath(),source.method()); }
}
