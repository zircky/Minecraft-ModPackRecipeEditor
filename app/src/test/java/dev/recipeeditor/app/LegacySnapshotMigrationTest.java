package dev.recipeeditor.app;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.app.release.LegacySnapshotMigration;
import dev.recipeeditor.companionclient.RuntimeSnapshotService;
import dev.recipeeditor.companionprotocol.RuntimeData.*;
import dev.recipeeditor.core.ApplicationDirectories;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@org.junit.jupiter.api.Tag("integration")
final class LegacySnapshotMigrationTest {
    @TempDir Path root;

    @Test void explicitMigrationPreservesSourceAndNeverReplacesDestination() throws Exception {
        final var directories = new ApplicationDirectories(root.resolve("roaming"), root.resolve("local"), root.resolve("legacy"));
        final var old = Files.createDirectories(directories.legacy().resolve("runtime-snapshots")).resolve("test.mresnapshot");
        final var data = new Snapshot(1, "test", true, new Environment("test", root.toString(), "1.20.1", "FORGE", "47.4.10",
                "test-version", 1, "2026-11-18T00:00:00Z"), List.of(), List.of(), List.of(), List.of(), List.of());
        new RuntimeSnapshotService().save(old, data);
        final byte[] source = Files.readAllBytes(old);
        final var migration = new LegacySnapshotMigration();
        assertEquals(1, migration.migrate(directories));
        final var destination = directories.snapshots().resolve("test.mresnapshot");
        assertArrayEquals(source, Files.readAllBytes(destination));
        assertArrayEquals(source, Files.readAllBytes(old));
        Files.writeString(destination, "existing user file");
        assertEquals(0, migration.migrate(directories));
        assertEquals("existing user file", Files.readString(destination));
    }

    @Test void corruptLegacyDataIsNotImportedOrRemoved() throws Exception {
        final var directories = new ApplicationDirectories(root.resolve("roaming"), root.resolve("local"), root.resolve("legacy"));
        final var old = Files.createDirectories(directories.legacy().resolve("runtime-snapshots")).resolve("broken.mresnapshot");
        Files.writeString(old, "broken");
        assertThrows(IOException.class, () -> new LegacySnapshotMigration().migrate(directories));
        assertEquals("broken", Files.readString(old));
        assertFalse(Files.exists(directories.snapshots().resolve(old.getFileName())));
    }
}
