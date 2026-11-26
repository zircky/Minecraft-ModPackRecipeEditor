package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.RecipeFilterMode;
import java.util.Locale;

record RecipeFilterState(String query, RecipeFilterMode modMode, String mod, String type, String change, String language) {
    RecipeFilterState {
        query = query.strip().toLowerCase(Locale.ROOT);
    }
}
