package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.util.*;

final class ParserSupport {
    private ParserSupport() { }
    static ModMetadataParseResult failure(final String source, final Exception error) {
        return new ModMetadataParseResult(List.of(), List.of(new ImportDiagnostic(ScanStatus.ERROR, source,
                "Invalid metadata: " + error.getMessage())));
    }
    static void validate(final ModMetadata mod, final List<ImportDiagnostic> diagnostics) {
        if (mod.id().isBlank()) diagnostics.add(new ImportDiagnostic(ScanStatus.ERROR, mod.metadataSource(), "Missing mod ID"));
        if (mod.version().equals("UNKNOWN") || mod.version().contains("${"))
            diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, mod.metadataSource(), "Unresolved mod version: " + mod.version()));
    }
}
