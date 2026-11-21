package dev.recipeeditor.registry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ResourceDiagnosticsTest {
    @Test void reportsActualMissingAssetsAndTranslationsWithSourceWithoutErrorSpam() {
        final var document=ResourceFixtures.document("assets/test/models/item/a.json","{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"test:item/missing\"}}");
        final var scan=new ResourceScanResult(List.of(document),List.of(),List.of());
        final var entries=new ItemResourceIndexer().index(scan,new LocalizationService(scan.documents()),new TagIndex(List.of()),"en_us",new ArrayList<>());
        final var diagnostics=new ResourceDiagnostics();final var index=new RegistryIndex(entries);final var rows=diagnostics.inspect(index,"ru_ru").toList();
        assertEquals(2,rows.size());assertEquals(1L,diagnostics.summarize(index,"ru_ru").get("RESOURCE_TEXTURE_NOT_FOUND"));
        assertEquals(1L,diagnostics.summarize(index,"ru_ru").get("RESOURCE_TRANSLATION_MISSING"));
        assertTrue(rows.stream().allMatch(row->row.id().toString().equals("test:a")&&row.sources().contains(document.source())&&row.severity().equals("WARNING")));
    }
}
