package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.nio.file.Path;
import java.util.List;
public record DetectionResult(Path selectedDirectory, Path gameDirectory, String structure, Confidence confidence,
        List<ImportDiagnostic> diagnostics) {
    public enum Confidence { HIGH, MEDIUM, NONE }
    public DetectionResult { diagnostics = List.copyOf(diagnostics); }
    public boolean recognized() { return confidence != Confidence.NONE; }
}
