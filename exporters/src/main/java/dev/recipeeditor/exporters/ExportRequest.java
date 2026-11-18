package dev.recipeeditor.exporters;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.RecipeDefinition;
import java.util.*;
public record ExportRequest(ExportContext context,Set<ResourceLocation> selectedIds,Map<ResourceLocation,ExportFormat> choices,List<RecipeDefinition> unchangedCopies) {
    public ExportRequest {selectedIds=Set.copyOf(selectedIds);choices=Map.copyOf(choices);unchangedCopies=List.copyOf(unchangedCopies);}
}
