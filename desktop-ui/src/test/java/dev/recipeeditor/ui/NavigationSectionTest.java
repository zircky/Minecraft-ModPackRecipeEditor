package dev.recipeeditor.ui;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NavigationSectionTest {
    @Test
    void exposesRequiredNavigationInOrder() {
        assertEquals(java.util.List.of("Projects", "Mods", "Items", "Blocks", "Fluids", "Recipes", "Runtime", "Analyzer", "Settings"),
                Arrays.stream(NavigationSection.values()).map(NavigationSection::title).toList());
    }

    @Test
    void everySectionExplainsItsEmptyState() {
        for (final var section : NavigationSection.values()) {
            assertFalse(section.emptyTitle().isBlank());
            assertFalse(section.description().isBlank());
        }
    }
}
