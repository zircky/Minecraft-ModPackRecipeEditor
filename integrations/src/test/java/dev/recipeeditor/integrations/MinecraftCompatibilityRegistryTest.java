package dev.recipeeditor.integrations;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

final class MinecraftCompatibilityRegistryTest {
    @ParameterizedTest
    @CsvSource({"1.7.10,LEGACY,false", "1.11.2,LEGACY,false", "1.12.2,EARLY_JSON,false",
            "1.13,DATAPACK,false", "1.16.5,DATAPACK,false", "1.20.1,DATAPACK,true",
            "1.21.1,MODERN_DATAPACK,false"})
    void selectsDiscoveryGenerationSeparatelyFromEditing(final String version,
            final RecipeFormatAdapter.Generation generation, final boolean editable) {
        final var selected = new MinecraftCompatibilityRegistry().find(version).orElseThrow();
        assertEquals(generation, selected.generation());
        assertEquals(editable, selected.editingSupported());
        assertEquals(editable, !selected.parsers().isEmpty());
    }

    @Test void missingAndAmbiguousAdaptersFailClosed() {
        assertTrue(new MinecraftCompatibilityRegistry().find("UNKNOWN").isEmpty());
        assertTrue(new MinecraftCompatibilityRegistry().find("1.21-pre1").isEmpty());
        final var existing = new Minecraft1201Integration();
        assertTrue(new MinecraftCompatibilityRegistry(List.of(existing, existing)).find("1.20.1").isEmpty());
        assertTrue(new MinecraftCompatibilityRegistry(List.of(existing)).find("1.21.1").isEmpty());
    }
}
