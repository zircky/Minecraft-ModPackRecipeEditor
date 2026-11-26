package dev.recipeeditor.companionclient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in evidence from a real saved database. Reads the existing snapshot without opening a socket. */
@Tag("external")
@EnabledIfSystemProperty(named = "runtimeSnapshotFile", matches = ".+")
final class RuntimeRecipeTypeDiscoveryTest {
    @Test void reportsCapabilitiesOfPreviouslyCapturedLiveTypesOffline() throws Exception {
        final Path database = Path.of(System.getProperty("runtimeSnapshotFile")).toAbsolutePath();
        final var fingerprint = new ModpackFingerprint(database.getParent().getFileName().toString());
        final var store = new SqliteSnapshotStore(database.getParent().getParent());
        assertEquals(database, store.database(fingerprint));
        final var snapshot = store.loadLatest(fingerprint).orElseThrow();
        final var types = store.recipeTypes(fingerprint, snapshot);
        assertFalse(types.isEmpty());
        assertTrue(types.stream().anyMatch(type -> type.type().equals("minecraft:crafting")));
        final var report = new ArrayList<String>();
        report.add("Snapshot: " + snapshot.snapshotId() + " / " + snapshot.environment().collectedAt());
        report.add("Discovered types: " + types.size());
        report.add("Registered: " + types.stream().filter(RecipeTypeRegistry.Descriptor::registered).count());
        report.add("Discovered recipes: " + snapshot.recipes().size());
        for (final var operation : RecipeTypeRegistry.Operation.values()) {
            report.add(operation + ": " + types.stream().filter(type -> type.capabilities().contains(operation)).count());
        }
        report.add("");
        types.forEach(type -> report.add(type.type() + "\t" + type.recipes() + "\t"
                + new TreeSet<>(type.serializers()) + "\t" + new TreeSet<>(type.capabilities())));
        Files.write(Files.createDirectories(Path.of("build")).resolve("phase24-type-matrix.tsv"), report);
    }
}
