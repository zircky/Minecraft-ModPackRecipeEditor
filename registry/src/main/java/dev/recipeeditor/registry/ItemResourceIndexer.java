package dev.recipeeditor.registry;

import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.util.List;
public final class ItemResourceIndexer {
    public List<RegistryEntry> index(final ResourceScanResult scan,final LocalizationService localization,final TagIndex tags,
            final String language,final List<ImportDiagnostic> diagnostics) {
        return new ResourceEntryBuilder().build(ResourceKind.ITEM,scan,localization,tags,language,diagnostics);
    }
    public List<RegistryEntry> index(final ResourceScanResult scan,final LocalizationService localization,final TagIndex tags,
            final String language,final List<ImportDiagnostic> diagnostics,final List<ResourceReference> references) {
        return new ResourceEntryBuilder().build(ResourceKind.ITEM,scan,localization,tags,language,diagnostics,references);
    }
}
