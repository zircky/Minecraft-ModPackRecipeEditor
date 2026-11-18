package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.RecipeChangeType;
import java.util.*;
public record ExportCapability(ExportSupportStatus status,String reason,Set<RecipeChangeType> operations,Map<String,ExportSupportStatus> properties) {
    public ExportCapability { operations=Set.copyOf(operations); properties=Map.copyOf(properties); }
    public boolean supported() { return status==ExportSupportStatus.SUPPORTED; }
    public static ExportCapability supported(final String reason) { return new ExportCapability(ExportSupportStatus.SUPPORTED,reason,Set.of(RecipeChangeType.CREATE,RecipeChangeType.UPDATE,RecipeChangeType.DELETE),Map.of()); }
    public static ExportCapability unsupported(final String reason) { return new ExportCapability(ExportSupportStatus.UNSUPPORTED,reason,Set.of(),Map.of()); }
}
