package dev.recipeeditor.registry;

import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.util.List;
public final class BlockResourceIndexer {
    public List<RegistryEntry> index(final ResourceScanResult scan,final LocalizationService localization,final TagIndex tags,
            final String language,final List<ImportDiagnostic> diagnostics) {
        return new ResourceEntryBuilder().build(ResourceKind.BLOCK,scan,localization,tags,language,diagnostics);
    }
}
