package dev.recipeeditor.integrations;

import dev.recipeeditor.recipeengine.RecipeParser;
import dev.recipeeditor.registry.ResourceLayout;
import java.util.List;

public interface RecipeFormatAdapter {
    enum Generation { LEGACY, EARLY_JSON, DATAPACK, MODERN_DATAPACK }

    Generation generation();
    boolean supports(String minecraftVersion);
    ResourceLayout layout();
    default List<RecipeParser> parsers() { return List.of(); }
    default boolean editingSupported() { return false; }
}
