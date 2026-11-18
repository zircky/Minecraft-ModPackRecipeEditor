package dev.recipeeditor.exporters;

import java.util.List;
import dev.recipeeditor.core.ResourceLocation;
public record ExportDiagnostic(Level level,Code code,String message,List<ResourceLocation> recipeIds,ExportFormat format,String suggestedAction) {
    public enum Level { INFO, WARNING, ERROR }
    public enum Code { EXPORT_UNSUPPORTED_RECIPE_TYPE, EXPORT_MISSING_DEPENDENCY, EXPORT_INVALID_RECIPE, EXPORT_FILE_CONFLICT, EXPORT_VERSION_MISMATCH, EXPORT_WRITE_FAILED, EXPORT_SOURCE_CONFLICT, EXPORT_AMBIGUOUS_CHOICE, EXPORT_CANCELLED, EXPORT_SUCCESS, EXPORT_RUNTIME_UNVERIFIED }
    public ExportDiagnostic { recipeIds=List.copyOf(recipeIds); }
}
