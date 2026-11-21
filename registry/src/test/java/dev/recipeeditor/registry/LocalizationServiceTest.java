package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class LocalizationServiceTest {
    @Test void blockItemsUseExistingBlockTranslationsWithoutInventingItemNames() {
        final var service=new LocalizationService(List.of(ResourceFixtures.document("assets/example/lang/en_us.json","{\"block.example.planks\":\"Actual Planks\"}")));
        final var id=ResourceLocation.parse("example:planks");
        assertEquals("block.example.planks",service.key(ResourceKind.ITEM,id));
        assertEquals("Actual Planks",service.displayName(id,service.key(ResourceKind.ITEM,id),"ru_ru"));
        final var unknown=ResourceLocation.parse("example:missing");
        assertEquals(unknown.toString(),service.displayName(unknown,service.key(ResourceKind.ITEM,unknown),"en_us"));
    }
    @Test void blankRussianFallsBackAndNumericComponentArgumentsAreSupported() {
        final var service=new LocalizationService(List.of(ResourceFixtures.document("assets/test/lang/en_us.json","{\"item.test.a\":\"English\",\"count.test\":\"Count %s\"}"),ResourceFixtures.document("assets/test/lang/ru_ru.json","{\"item.test.a\":\"   \"}")));
        assertEquals("English",service.displayName(ResourceLocation.parse("test:a"),"item.test.a","ru_ru"));
        assertEquals("Count 42",service.componentTranslations("{\"translate\":\"count.test\",\"with\":[42]}").get("en_us"));
    }
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
    @Test void actualModSpecificComponentKeyAndArgumentsUseTranslationsWithoutInventingNames() {
        final var service=new LocalizationService(List.of(ResourceFixtures.document("assets/gtceu/lang/en_us.json","{\"material.gtceu.propane\":\"Propane\",\"prefix.ingot\":\"%s Ingot\"}")));
        assertEquals("Propane",service.componentTranslations("{\"translate\":\"material.gtceu.propane\"}").get("en_us"));
        assertEquals("Propane Ingot",service.componentTranslations("{\"translate\":\"prefix.ingot\",\"with\":[{\"translate\":\"material.gtceu.propane\"}]}").get("ru_ru"));
        assertTrue(service.componentTranslations("{\"translate\":\"unknown.resource\"}").isEmpty());
        assertTrue(service.translations("prefix.ingot").isEmpty());
        assertTrue(service.componentTranslations("{\"translate\":\"prefix.ingot\"}").isEmpty());
        assertTrue(service.resolvedRuntimeName("Propane","{\"translate\":\"material.gtceu.propane\"}"));
        assertFalse(service.resolvedRuntimeName("prefix.ingot(material.missing)","{\"translate\":\"prefix.ingot\",\"with\":[{\"translate\":\"material.missing\"}]}"));
    }
    @Test void conflictingTranslationsHaveNoArbitraryPackWinner() {
        final var service=new LocalizationService(List.of(ResourceFixtures.document("assets/a/lang/en_us.json","{\"fluid.test.a\":\"One\"}"),ResourceFixtures.document("assets/b/lang/en_us.json","{\"fluid.test.a\":\"Two\"}")));
        assertEquals("test:a",service.displayName(ResourceLocation.parse("test:a"),"fluid.test.a","en_us"));assertFalse(service.diagnostics().isEmpty());
    }
}
