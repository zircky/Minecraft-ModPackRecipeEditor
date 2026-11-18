package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.util.List;
public final class MinecraftVersionDetector {
    public MinecraftVersion detect(final MetadataEvidence evidence, final List<ImportDiagnostic> diagnostics) {
        final var distinct = evidence.versions().stream().map(MetadataEvidence.VersionEvidence::version).distinct().toList();
        final var sources = evidence.versions().stream().map(v -> v.source() + " = " + v.version()).toList();
        if (distinct.size() > 1) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,"Minecraft", "Conflicting Minecraft versions: " + sources));
        if (distinct.isEmpty()) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,"Minecraft", "Minecraft version is UNKNOWN"));
        return new MinecraftVersion(distinct.size() == 1 ? distinct.getFirst() : "UNKNOWN", sources);
    }
}
