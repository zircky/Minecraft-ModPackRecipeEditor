package dev.recipeeditor.companionclient;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class SnapshotMigrationTest {
    @TempDir Path root;

    @Test void refusesFutureSchemaWithoutRewritingIt() throws Exception {
        final var store = new SqliteSnapshotStore(root.resolve("cache"));
        final var key = ModpackFingerprint.calculate(SnapshotFingerprintTest.pack(root));
        final var snapshot = new RuntimeFixtures().snapshot(root, "session", 1);
        store.save(key, snapshot);
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + store.database(key));
                var statement = db.createStatement()) {
            statement.executeUpdate("PRAGMA user_version = 99");
        }
        assertEquals(SnapshotStatus.INCOMPATIBLE, new SnapshotLifecycle(store).inspect(key, key).status());
        assertThrows(SqliteSnapshotStore.IncompatibleSchema.class, () -> store.save(key, snapshot));
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + store.database(key));
                var statement = db.createStatement();
                var version = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(version.next());
            assertEquals(99, version.getInt(1));
        }
    }
}
