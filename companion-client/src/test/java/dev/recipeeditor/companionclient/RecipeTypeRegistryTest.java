package dev.recipeeditor.companionclient;

import com.google.gson.JsonParser;
import dev.recipeeditor.companionprotocol.RuntimeData.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.recipeeditor.companionclient.RecipeTypeRegistry.Operation.*;

final class RecipeTypeRegistryTest {
    @TempDir Path root;

    @Test void includesRegisteredEmptyTypesAndObservedUnregisteredTypesWithoutInferringOwnership() throws Exception {
        final var original = new RuntimeFixtures().snapshot(root, "session", 1);
        final var empty = new Registry(Kind.RECIPE_TYPE, "test:empty_machine", "Empty", "test.Empty", Map.of(), Map.of());
        final var unknown = new Recipe("test:a", "test:loaded", "test:serializer", "test.Class", "generic",
                Support.UNSUPPORTED, new com.google.gson.JsonObject(), "UNKNOWN", List.of());
        final var snapshot = new Snapshot(1, "inventory", true, original.environment(), List.of(empty),
                List.of(), List.of(unknown, cooking()), original.mods(), List.of());
        final var key = new ModpackFingerprint("e".repeat(64));
        final var expected = new RecipeTypeRegistry().discover(snapshot);
        final var store = new SqliteSnapshotStore(root.resolve("cache"));
        store.save(key, snapshot);
        assertEquals(snapshot, store.loadLatest(key).orElseThrow());
        assertEquals(expected, store.recipeTypes(key, snapshot));
        assertEquals(3, expected.size());
        final var registered = expected.stream().filter(row -> row.type().equals("test:empty_machine")).findFirst().orElseThrow();
        assertTrue(registered.registered());
        assertEquals(0, registered.recipes());
        assertTrue(registered.serializers().isEmpty());
        assertEquals("UNKNOWN", registered.sourceMod());
        final var observed = expected.stream().filter(row -> row.type().equals("test:loaded")).findFirst().orElseThrow();
        assertFalse(observed.registered());
        assertEquals(Set.of(VIEW), observed.capabilities());
        assertEquals(Set.of("test:serializer"), observed.serializers());
        final var vanilla = expected.stream().filter(row -> row.type().equals("minecraft:smelting")).findFirst().orElseThrow();
        assertEquals(Set.of(VIEW, CREATE, DUPLICATE, EXPORT_JSON), vanilla.capabilities());
        assertTrue(vanilla.properties().contains("cookingTime"));
    }

    @Test void legacyGenerationCanDeriveCatalogFromSavedWireEvidence() throws Exception {
        final var snapshot = new RuntimeFixtures().snapshot(root, "session", 1);
        final var key = new ModpackFingerprint("a".repeat(64));
        final var store = new SqliteSnapshotStore(root);
        store.save(key, snapshot);
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + store.database(key));
                var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE recipe_types");
        }
        assertEquals(new RecipeTypeRegistry().discover(snapshot), store.recipeTypes(key, snapshot));
    }

    static Recipe cooking() {
        final var properties = JsonParser.parseString("""
                {"group":"test","category":"misc","experience":0.25,"cookingTime":250,"sourceJsonAvailable":false,
                 "ingredients":[{"tag":"forge:ore"}],"result":{"item":"minecraft:iron_ingot","count":1,"empty":false}}
                """).getAsJsonObject();
        return new Recipe("test:smelt", "minecraft:smelting", "minecraft:smelting", "minecraft.SmeltingRecipe",
                "vanilla-1.20.1", Support.SUPPORTED, properties, "Runtime", List.of());
    }
}
