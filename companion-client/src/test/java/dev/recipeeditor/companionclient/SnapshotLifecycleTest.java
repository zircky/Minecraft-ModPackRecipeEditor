package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData.Snapshot;
import java.io.IOException;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class SnapshotLifecycleTest {
    @TempDir Path root;

    @Test void distinguishesMissingStaleCurrentAndCorruptAndRequiresConsentForStale() throws Exception {
        final var pack = SnapshotFingerprintTest.pack(root);
        final var store = new SqliteSnapshotStore(root.resolve("cache"));
        final var lifecycle = new SnapshotLifecycle(store);
        final var previous = ModpackFingerprint.calculate(pack);
        assertEquals(SnapshotStatus.NOT_AVAILABLE, lifecycle.inspect(previous, previous).status());
        final var snapshot = new RuntimeFixtures().snapshot(root, "session", 1);
        lifecycle.publish(pack, previous, snapshot);
        assertEquals(SnapshotStatus.AVAILABLE, lifecycle.inspect(previous, previous).status());
        Files.writeString(root.resolve("server.properties"), "example=changed");
        final var current = ModpackFingerprint.calculate(pack);
        assertEquals(SnapshotStatus.STALE, lifecycle.inspect(current, previous).status());
        assertEquals(SnapshotStatus.STALE,
                new SnapshotLifecycle(new SqliteSnapshotStore(root.resolve("cache"))).inspect(pack, current).status());
        assertThrows(IOException.class, () -> lifecycle.open(current, previous, false));
        assertEquals(snapshot, lifecycle.open(current, previous, true).orElseThrow());
        assertThrows(IOException.class, () -> lifecycle.publish(pack, previous, snapshot));
        assertEquals(snapshot, store.loadLatest(previous).orElseThrow());
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + store.database(previous));
                var statement = db.createStatement()) {
            statement.executeUpdate("DELETE FROM recipes");
        }
        assertEquals(SnapshotStatus.CORRUPTED, lifecycle.inspect(current, previous).status());
    }

    @Test void failedWriteAndPartialSnapshotPreserveTheLastCompleteGeneration() throws Exception {
        final var fingerprint = ModpackFingerprint.calculate(SnapshotFingerprintTest.pack(root));
        final var store = new SqliteSnapshotStore(root.resolve("cache"));
        final var snapshot = new RuntimeFixtures().snapshot(root, "session", 1);
        store.save(fingerprint, snapshot);
        final var partial = new Snapshot(1, "partial", false, snapshot.environment(), snapshot.registries(),
                snapshot.tags(), snapshot.recipes(), snapshot.mods(), snapshot.diagnostics());
        assertThrows(IOException.class, () -> store.save(fingerprint, partial));
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + store.database(fingerprint));
                var statement = db.createStatement()) {
            statement.executeUpdate("""
                    CREATE TRIGGER reject_recipes BEFORE INSERT ON recipes
                    BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END
                    """);
        }
        assertThrows(IOException.class, () -> store.save(fingerprint, new RuntimeFixtures().snapshot(root, "session", 2)));
        assertEquals(snapshot, store.loadLatest(fingerprint).orElseThrow());
    }
}
