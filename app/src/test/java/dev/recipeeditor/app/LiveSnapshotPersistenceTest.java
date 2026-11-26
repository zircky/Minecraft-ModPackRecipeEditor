package dev.recipeeditor.app;

import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.companionprotocol.ItemPreviewRequest;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.registry.LocalizationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in probe against an existing local world. Writes evidence only to the app build directory. */
@Tag("external")
@EnabledIfSystemProperty(named = "previewSession", matches = ".+")
@EnabledIfSystemProperty(named = "realModpack", matches = ".+")
final class LiveSnapshotPersistenceTest {
    private static final List<String> ITEMS = List.of("gtceu:americium_ingot", "gtceu:americium_rod",
            "gtceu:americium_plate", "gtceu:aluminium_block");

    @Test void publishesLiveSnapshotAndReopensItAfterDesktopDisconnect() throws Exception {
        final Path output = Files.createDirectories(Path.of("build/phase22-live"));
        final Path cache = output.resolve("cache");
        final var store = new SqliteSnapshotStore(cache);
        final var lifecycle = new SnapshotLifecycle(store);
        final RuntimeData.Snapshot captured;
        final ModpackFingerprint fingerprint;
        try (var client = new CompanionConnectionService(); var importer = new DefaultModpackImportService()) {
            client.connect(Path.of(System.getProperty("previewSession"))).get(15, TimeUnit.SECONDS);
            assertEquals(CompanionConnectionStatus.CONNECTED, client.status());
            System.out.println("Authenticated protocol 1; Companion " + client.companionVersion());
            final var pack = importer.start(Path.of(System.getProperty("realModpack")), event -> {})
                    .result().get(90, TimeUnit.SECONDS).modpack();
            fingerprint = ModpackFingerprint.calculate(pack);
            captured = client.synchronize(System.out::println).get(310, TimeUnit.SECONDS);
            System.out.printf("Captured %d registry rows, %d recipes, %d materials%n",
                    captured.registries().size(), captured.recipes().size(), captured.materials().size());
            capturePreviewEvidence(client, captured, output);
            final Path db = lifecycle.publish(pack, fingerprint, captured);
            Files.writeString(output.resolve("database.txt"), db.toAbsolutePath().toString());
            client.disconnect();
            assertEquals(CompanionConnectionStatus.DISCONNECTED, client.status());
        }

        final var reopened = new SnapshotLifecycle(new SqliteSnapshotStore(cache))
                .open(fingerprint, fingerprint, false).orElseThrow();
        assertTrue(captured.equals(reopened), "Reopened snapshot differs from the live capture");
        assertFalse(reopened.materials().isEmpty(), "Expected actual GTCEu materials");
        assertFalse(reopened.recipes().isEmpty(), "Expected live recipes");
        final long machineRecipes = reopened.recipes().stream().filter(row -> row.type().startsWith("gtceu:")).count();
        System.out.println("GTCEu recipe types: " + machineRecipes + " rows");
        final var sample = reopened.recipes().stream().filter(row -> row.type().startsWith("gtceu:")
                && row.status() == RuntimeData.Support.SUPPORTED).findFirst();
        sample.ifPresent(row -> System.out.println("Supported GTCEu sample " + row.id()
                + ", adapter " + row.adapter() + ", property keys " + row.properties().keySet()));
        Files.writeString(output.resolve("localized-names.tsv"), observedNames(reopened));
        System.out.println("SQLite comparison passed after disconnect.");
    }

    private String observedNames(final RuntimeData.Snapshot snapshot) {
        final var report = new StringBuilder("ID\tTranslation key\tName\tLanguage\tSource\tStorage\n");
        final var localization = new LocalizationService(List.of());
        for (final String id : ITEMS) {
            final var row = snapshot.registries().stream()
                    .filter(entry -> entry.kind() == RuntimeData.Kind.ITEM && entry.id().equals(id)).findFirst();
            if (row.isEmpty()) {
                report.append(id).append("\t—\t—\t—\tITEM not registered\t—\n");
                continue;
            }
            final var metadata = row.orElseThrow().metadata();
            final String locale = metadata.getOrDefault("runtimeNameLanguage", "");
            final String name = metadata.get("name." + locale);
            final boolean resolved = !id.equals(name)
                    && localization.resolvedRuntimeName(name, metadata.get("nameComponent"));
            report.append(String.join("\t", id, metadata.getOrDefault("localizationKey", "—"),
                    resolved ? name : "Unavailable", locale.isBlank() ? "—" : locale,
                    resolved ? "Minecraft client" : "No resolved client name",
                    resolved ? "Reopened from SQLite" : "Registry record reopened")).append('\n');
        }
        return report.toString();
    }

    private void capturePreviewEvidence(final CompanionConnectionService client, final RuntimeData.Snapshot snapshot,
            final Path output) throws Exception {
        if (!client.itemPreviewsAvailable()) {
            System.out.println(client.previewUnavailableReason());
            return;
        }
        final var requests = ITEMS.stream().filter(id -> snapshot.registries().stream()
                .anyMatch(row -> row.kind() == RuntimeData.Kind.ITEM && row.id().equals(id)))
                .map(id -> new ItemPreviewRequest(id, 128)).toList();
        if (requests.isEmpty()) return;
        for (final var response : client.itemPreviews(requests).get(15, TimeUnit.SECONDS)) {
            System.out.printf("%s: %s (%s)%n", response.request().resourceId(), response.status(), response.diagnostic());
            if (!response.status().equals("READY")) continue;
            final var image = ImageIO.read(new java.io.ByteArrayInputStream(response.png()));
            assertNotNull(image, response.request().resourceId());
            Files.write(output.resolve(response.request().resourceId().replace(':', '_') + ".png"), response.png());
        }
        System.out.println(client.previewStatistics());
    }
}
