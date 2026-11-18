package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.util.List;
public record RecipeParseResult(RecipeDefinition recipe,List<ImportDiagnostic> diagnostics) {
    public RecipeParseResult { diagnostics=List.copyOf(diagnostics); }
}
