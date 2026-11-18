package dev.recipeeditor.core.importing;

import java.util.Objects;
public record ImportDiagnostic(ScanStatus severity, String source, String message) {
    public ImportDiagnostic { Objects.requireNonNull(severity); Objects.requireNonNull(source); Objects.requireNonNull(message); }
}
