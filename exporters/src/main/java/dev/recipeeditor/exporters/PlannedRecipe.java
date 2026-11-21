package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.RecipeChange;
import dev.recipeeditor.recipeengine.RecipeProvenance;
import java.util.List;
public record PlannedRecipe(RecipeChange change,ExportFormat format,ExportCapability capability,List<ExportFormat> alternatives,RecipeProvenance provenance,String exportMode,List<String> targetFiles) {public PlannedRecipe {alternatives=List.copyOf(alternatives);targetFiles=List.copyOf(targetFiles);java.util.Objects.requireNonNull(provenance);}}
