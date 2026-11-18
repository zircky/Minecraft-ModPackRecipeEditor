package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.util.List;
public record RecipeDefinition(ResourceLocation id,RecipeTypeDefinition type,List<IngredientDefinition> inputs,List<RecipeOutput> outputs,
        RecipeLayout layout,List<RecipeProperty> properties,RecipeSource source,String rawJson,RecipeParseStatus status,List<ImportDiagnostic> diagnostics) {
    public RecipeDefinition { inputs=List.copyOf(inputs); outputs=List.copyOf(outputs); properties=List.copyOf(properties); diagnostics=List.copyOf(diagnostics); }
}
