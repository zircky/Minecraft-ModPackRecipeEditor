package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.util.List;
public final class ModLoaderDetector {
    public ModLoaderDefinition detect(final MetadataEvidence evidence, final List<ImportDiagnostic> diagnostics) {
        final var types = evidence.loaders().stream().map(MetadataEvidence.LoaderEvidence::type).distinct().toList();
        final var versions = evidence.loaders().stream().map(MetadataEvidence.LoaderEvidence::version).filter(v -> !v.equals("UNKNOWN")).distinct().toList();
        final var sources = evidence.loaders().stream().map(v -> v.source() + " = " + v.type() + " " + v.version()).toList();
        if (types.size() > 1 || versions.size() > 1) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,"Loader", "Conflicting loader metadata: " + sources));
        if (types.isEmpty()) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,"Loader", "Loader is UNKNOWN; mod metadata is not proof of the installed loader"));
        return new ModLoaderDefinition(types.size() == 1 ? types.getFirst() : ModLoaderType.UNKNOWN,
                types.size() == 1 && versions.size() == 1 ? versions.getFirst() : "UNKNOWN", sources);
    }
}
