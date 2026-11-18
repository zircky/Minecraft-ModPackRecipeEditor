package dev.recipeeditor.core.importing;

import java.util.List;
public record ModpackImportResult(ModpackDefinition modpack, List<ImportDiagnostic> diagnostics) {
    public ModpackImportResult { diagnostics = List.copyOf(diagnostics); }
}
