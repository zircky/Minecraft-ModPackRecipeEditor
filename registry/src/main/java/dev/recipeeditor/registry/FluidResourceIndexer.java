package dev.recipeeditor.registry;

import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.util.List;
public final class FluidResourceIndexer {
    public List<RegistryEntry> index(final ResourceScanResult scan,final LocalizationService localization,final TagIndex tags,
            final String language,final List<ImportDiagnostic> diagnostics) {
        return new ResourceEntryBuilder().build(ResourceKind.FLUID,scan,localization,tags,language,diagnostics);
    }
}
