package dev.recipeeditor.registry;

import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.util.List;
public record ResourceScanResult(List<ResourceDocument> documents, List<ResourceSource> textures, List<ImportDiagnostic> diagnostics) {
    public ResourceScanResult { documents=List.copyOf(documents); textures=List.copyOf(textures); diagnostics=List.copyOf(diagnostics); }
}
