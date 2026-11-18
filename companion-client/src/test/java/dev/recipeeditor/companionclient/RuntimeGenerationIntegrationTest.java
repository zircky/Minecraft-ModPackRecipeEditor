package dev.recipeeditor.companionclient;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.companionserver.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Actual localhost protocol with a synthetic provider; never claims Minecraft API coverage. */
@Tag("integration")
final class RuntimeGenerationIntegrationTest {
    @TempDir Path root;

    @Test
    void reloadDuringTransferRejectsSnapshotAndNextSessionCanSynchronize() throws Exception {
        final var generation = new AtomicLong(1);
        final SnapshotProvider provider = new SnapshotProvider() {
            @Override public long generation() { return generation.get(); }
            @Override public boolean ready() { return true; }
            @Override public CompletableFuture<RuntimeData.Snapshot> capture(final String session,
                    final BooleanSupplier cancelled, final Consumer<String> progress) {
                return CompletableFuture.completedFuture(new RuntimeFixtures().snapshot(root, session, generation.get()));
            }
        };
        try (final var server = new ReadOnlyCompanionServer(provider);
             final var client = new CompanionConnectionService()) {
            server.start();
            client.connect(server.credentials()).get(5, TimeUnit.SECONDS);
            final var failed = client.synchronize(stage -> {
                if (stage.equals("Receiving GET_TAGS")) generation.incrementAndGet();
            });
            assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
            assertEquals(CompanionConnectionStatus.ERROR, client.status());
        }
        // New session uses fresh environment/generation; no stale server state is reused.
        try (final var server = new ReadOnlyCompanionServer(provider);
             final var client = new CompanionConnectionService()) {
            server.start();
            client.connect(server.credentials()).get(5, TimeUnit.SECONDS);
            final var snapshot = client.synchronize(stage -> {}).get(5, TimeUnit.SECONDS);
            assertTrue(snapshot.complete());
            assertEquals(2, snapshot.environment().generation());
            assertEquals(CompanionConnectionStatus.CONNECTED, client.status());
        }
    }
}
