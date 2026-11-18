package dev.recipeeditor.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ApplicationInfoTest {
    @Test
    void preservesApplicationIdentity() {
        final var info = new ApplicationInfo("Recipe Editor", "0.1.0");
        assertEquals("Recipe Editor", info.name());
        assertEquals("0.1.0", info.version());
    }

    @Test
    void rejectsMissingIdentity() {
        assertThrows(NullPointerException.class, () -> new ApplicationInfo(null, "1"));
        assertThrows(NullPointerException.class, () -> new ApplicationInfo("Editor", null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationInfo(" ", "1"));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationInfo("Editor", ""));
    }
}
