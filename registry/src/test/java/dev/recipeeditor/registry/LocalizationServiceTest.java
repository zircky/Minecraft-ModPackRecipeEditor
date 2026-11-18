package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class LocalizationServiceTest {
    private LocalizationService service() {
        return new LocalizationService(List.of(ResourceFixtures.document("assets/test/lang/en_us.json","{\"item.test.a\":\"English\"}"),
                ResourceFixtures.document("assets/test/lang/ru_ru.json","{\"item.test.a\":\"Русский\"}")));
    }
    @Test void languageAndFallback() {
        final var service=service(); final var id=ResourceLocation.parse("test:a");
        assertEquals("Русский",service.displayName(id,"item.test.a","ru_ru"));
        assertEquals("English",service.displayName(id,"item.test.a","en_us"));
        assertEquals("English",service.displayName(id,"item.test.a","fr_fr"));
        assertEquals("test:a",service.displayName(id,"item.test.missing","ru_ru"));
        assertEquals("item.test.a",service.key(ResourceKind.ITEM,id));
    }
    @Test void localizationAloneNeverCreatesRegistryEntries() {
        final var scan=new ResourceScanResult(List.of(ResourceFixtures.document("assets/test/lang/en_us.json","{\"item.test.a\":\"English\"}")),List.of(),List.of());
        assertTrue(new ItemResourceIndexer().index(scan,service(),new TagIndex(List.of()),"en_us",new java.util.ArrayList<>()).isEmpty());
    }
}
