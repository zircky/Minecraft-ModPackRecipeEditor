package dev.recipeeditor.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ApplicationDirectoriesTest {
    @TempDir Path root;

    @Test void usesWindowsRoamingAndLocalDirectoriesRatherThanWorkingDirectory() {
        final var roaming = root.resolve("Roaming с пробелом");
        final var local = root.resolve("Local");
        final var paths = ApplicationDirectories.resolve(Map.of("APPDATA", roaming.toString(), "LOCALAPPDATA", local.toString()), root);
        assertEquals(roaming.resolve("MinecraftRecipeEditor/settings.json"), paths.settings());
        assertEquals(local.resolve("MinecraftRecipeEditor/logs"), paths.logs());
        assertEquals(local.resolve("MinecraftRecipeEditor/snapshots"), paths.snapshots());
        assertEquals(root.resolve(".minecraft-recipe-editor"), paths.legacy());
    }

    @Test void fallsBackToUserHomeAndCreatesDirectoriesWithoutInventingSettings() throws Exception {
        final var paths = ApplicationDirectories.resolve(Map.of(), root);
        paths.create();
        assertTrue(Files.isDirectory(paths.temporary()));
        assertTrue(Files.isDirectory(paths.profiles()));
        assertFalse(Files.exists(paths.settings()), "Settings manager is not implemented by directory creation");
        assertEquals(root.resolve("AppData/Local/MinecraftRecipeEditor/cache"), paths.cache());
    }

    @Test void supportsIsolatedAbsoluteDataHomeForPortableSmokeTests() {
        final var paths = ApplicationDirectories.resolve(Map.of("MRE_DATA_HOME", root.resolve("test data").toString()), root);
        assertEquals(root.resolve("test data/local/logs"), paths.logs());
        assertEquals(root.resolve("test data/roaming/ui-state.json"), paths.uiState());
    }

    @Test void rejectsRelativeEnvironmentPaths() {
        assertThrows(IllegalArgumentException.class, () -> ApplicationDirectories.resolve(Map.of("MRE_DATA_HOME", "relative"), root));
        assertThrows(IllegalArgumentException.class, () -> ApplicationDirectories.resolve(Map.of("LOCALAPPDATA", "relative"), root));
    }
}
