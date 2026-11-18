package dev.recipeeditor.core.importing;

import java.nio.file.Path;
import java.util.List;
public record ModDefinition(ModMetadata metadata, Path sourceJar, ScanStatus status, List<ImportDiagnostic> diagnostics) {
    public ModDefinition { diagnostics = List.copyOf(diagnostics); }
}
