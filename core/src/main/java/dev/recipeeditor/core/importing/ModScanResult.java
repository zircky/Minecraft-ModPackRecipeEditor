package dev.recipeeditor.core.importing;

import java.nio.file.Path;
import java.util.List;
public record ModScanResult(Path sourceJar, List<ModDefinition> mods, ScanStatus status, List<ImportDiagnostic> diagnostics) {
    public ModScanResult { mods = List.copyOf(mods); diagnostics = List.copyOf(diagnostics); }
}
