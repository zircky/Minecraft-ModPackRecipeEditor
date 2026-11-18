package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.RecipeChange;
import java.util.*;
public interface RecipeExporter {
    ExportFormat format();
    ExportCapability checkCapability(RecipeChange change,ExportContext context);
    Map<String,String> generate(List<RecipeChange> changes,ExportContext context);
}
