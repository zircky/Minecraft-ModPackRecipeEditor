package dev.recipeeditor.exporters;

import java.util.List;
public record ExportResult(Status status,List<ExportDiagnostic> diagnostics,List<ExportManifest> manifests) {
    public enum Status { SUCCESS, FAILED, CANCELLED, RECOVERY_REQUIRED }
    public ExportResult {diagnostics=List.copyOf(diagnostics);manifests=List.copyOf(manifests);}
}
