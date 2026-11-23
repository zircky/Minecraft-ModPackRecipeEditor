package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class SnapshotPersistenceTest {
    @TempDir Path root;

    @Test void reopensAllDatasetsAndKeepsPreviousGenerationAfterRefresh() throws Exception {
        final var legacy = new RuntimeFixtures().snapshot(root, "session", 1);
        final var base = legacy.recipes().getFirst();
        final var properties = base.properties();
        properties.add("modded", com.google.gson.JsonParser.parseString(
                """
                {"conditions":[{"custom":{"nested":true,"optional":null}}],"duration":20,"EUt":128,"chance":0.75}
                """));
        final var recipe = new Recipe(base.id(), base.type(), base.serializer(), base.runtimeClass(), base.adapter(),
                base.status(), properties, base.sourceInformation(), base.diagnostics());
        final var material = new Material("test:iron", "Iron", "item.test.iron", java.util.Map.of("en_us", "Iron"),
                java.util.Map.of("color", "abcdef"), List.of("example"),
                List.of(new MaterialForm("ingot", Kind.ITEM, "minecraft:iron_ingot")), java.util.Map.of());
        final var latest = new Snapshot(2, "latest", true, legacy.environment(), legacy.registries(), legacy.tags(),
                List.of(recipe), legacy.mods(), List.of("evidence"), List.of(material));
        final var fingerprint = ModpackFingerprint.calculate(SnapshotFingerprintTest.pack(root));
        final Path cache = root.resolve("cache with spaces");
        final var store = new SqliteSnapshotStore(cache);
        store.save(fingerprint, legacy);
        store.save(fingerprint, latest);
        assertEquals(latest, new SqliteSnapshotStore(cache).loadLatest(fingerprint).orElseThrow());
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + store.database(fingerprint));
                var statement = db.createStatement();
                var rows = statement.executeQuery("SELECT snapshot_id FROM snapshots ORDER BY generation")) {
            assertTrue(rows.next());
            assertEquals(legacy.snapshotId(), rows.getString(1));
            assertTrue(rows.next());
            assertEquals(latest.snapshotId(), rows.getString(1));
            assertFalse(rows.next());
        }
    }

    @Test
    @Tag("external")
    @EnabledIfSystemProperty(named="runtimeSnapshotFile", matches=".+")
    void reopensPreviouslyCapturedRealSnapshotWithoutMinecraft() throws Exception {
        final var original = new RuntimeSnapshotService().load(Path.of(System.getProperty("runtimeSnapshotFile")));
        final var fingerprint = new ModpackFingerprint("a".repeat(64));
        new SqliteSnapshotStore(root).save(fingerprint, original);
        assertEquals(original, new SqliteSnapshotStore(root).loadLatest(fingerprint).orElseThrow());
        System.out.printf("Offline SQLite replay: %,d registry records, %,d recipes, %,d materials%n",
                original.registries().size(), original.recipes().size(), original.materials().size());
    }
}
