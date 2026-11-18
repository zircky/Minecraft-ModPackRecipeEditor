package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.RecipeChange;
import java.util.List;
public record PlannedRecipe(RecipeChange change,ExportFormat format,ExportCapability capability,List<ExportFormat> alternatives) {public PlannedRecipe {alternatives=List.copyOf(alternatives);}}
