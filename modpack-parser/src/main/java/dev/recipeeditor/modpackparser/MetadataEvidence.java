package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.util.List;
public record MetadataEvidence(List<VersionEvidence> versions, List<LoaderEvidence> loaders, List<ImportDiagnostic> diagnostics) {
    public MetadataEvidence { versions = List.copyOf(versions); loaders = List.copyOf(loaders); diagnostics = List.copyOf(diagnostics); }
    public record VersionEvidence(String version, String source) { }
    public record LoaderEvidence(ModLoaderType type, String version, String source) { }
}
