package dev.recipeeditor.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
final class ResourceLocationTest {
    @Test void identifiersAndDefaultNamespace() {
        assertEquals("minecraft:iron_ingot",ResourceLocation.parse("iron_ingot").toString());
        assertEquals(new ResourceLocation("gtceu","steel_ingot"),ResourceLocation.parse("gtceu:steel_ingot"));
        assertEquals("create:parts/press",ResourceLocation.parse("create:parts/press").toString());
    }
    @ParameterizedTest @ValueSource(strings={"",":x","mod:","Bad:x","m:Bad","m:a b","m:a:b","m:a\\b","m:тест"})
    void rejectsInvalidIdentifiers(final String value) { assertThrows(IllegalArgumentException.class,()->ResourceLocation.parse(value)); }
    @Test void rejectsNull() { assertThrows(NullPointerException.class,()->new ResourceLocation(null,"a")); }
}
