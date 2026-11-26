package dev.recipeeditor.companionclient;

import dev.recipeeditor.core.importing.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SnapshotFingerprintTest {
    @TempDir Path root;

    @Test void relocationPreservesIdentityAndEachRelevantContentChangeInvalidatesIt() throws Exception {
        final Path first = Files.createDirectory(root.resolve("first"));
        final Path second = Files.createDirectory(root.resolve("second"));
        final List<String> inputs = List.of("mods/test.jar", "datapacks/pack.zip", "kubejs/server_scripts/all.js",
                "resourcepacks/pack.zip", "config/pack.toml", "saves/world/serverconfig/test.toml",
                "saves/world/datapacks/pack.zip", "options.txt");
        for (final String input : inputs) {
            write(first, input, "resourcePacks:[\"first\",\"second\"]");
            write(second, input, "resourcePacks:[\"first\",\"second\"]");
        }
        write(first, "config/mre_companion/session.json", "first transient session");
        write(second, "config/mre_companion/session.json", "second transient session");
        final var expected = ModpackFingerprint.calculate(pack(first));
        assertEquals(expected, ModpackFingerprint.calculate(pack(second)));
        for (final String input : inputs) {
            write(second, input, "resourcePacks:[\"second\",\"first\"]");
            assertNotEquals(expected, ModpackFingerprint.calculate(pack(second)), input);
            write(second, input, "resourcePacks:[\"first\",\"second\"]");
        }
        assertNotEquals(expected, ModpackFingerprint.calculate(new ModpackDefinition(first, first, "test",
                new MinecraftVersion("1.20.2", List.of()), pack(first).loader(), List.of())));
    }

    static ModpackDefinition pack(final Path path) {
        return new ModpackDefinition(path, path, "test", new MinecraftVersion("1.20.1", List.of()),
                new ModLoaderDefinition(ModLoaderType.FORGE, "47.4.10", List.of()), List.of());
    }

    private void write(final Path pack, final String name, final String text) throws Exception {
        final Path path = pack.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
}
