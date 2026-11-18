package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.util.List;
public record ModMetadataParseResult(List<ModMetadata> mods, List<ImportDiagnostic> diagnostics) {
    public ModMetadataParseResult { mods = List.copyOf(mods); diagnostics = List.copyOf(diagnostics); }
}
