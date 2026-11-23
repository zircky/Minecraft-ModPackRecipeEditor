package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData.Snapshot;
import dev.recipeeditor.core.importing.ModpackDefinition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SnapshotLifecycle {
    public record Inspection(SnapshotStatus status, String detail) {}

    private final SqliteSnapshotStore store;

    public SnapshotLifecycle(final SqliteSnapshotStore store) {
        this.store = store;
    }

    public Inspection inspect(final ModpackDefinition pack, final ModpackFingerprint current) throws IOException {
        final var exact = inspect(current, current);
        if (exact.status() != SnapshotStatus.NOT_AVAILABLE) return exact;
        final var candidate = store.previousFor(pack.gameDirectory().toRealPath());
        return candidate.map(previous -> inspect(current, previous)).orElse(exact);
    }

    public Inspection inspect(final ModpackFingerprint current, final ModpackFingerprint candidate) {
        try {
            if (store.loadLatest(candidate).isEmpty()) {
                return new Inspection(SnapshotStatus.NOT_AVAILABLE, "No saved snapshot. Connect Minecraft to collect one.");
            }
            if (!current.equals(candidate)) {
                return new Inspection(SnapshotStatus.STALE, "This snapshot was collected with a different modpack fingerprint.");
            }
            return new Inspection(SnapshotStatus.AVAILABLE, "A verified snapshot is stored locally.");
        } catch (SqliteSnapshotStore.IncompatibleSchema failure) {
            return new Inspection(SnapshotStatus.INCOMPATIBLE, failure.getMessage());
        } catch (IOException failure) {
            return new Inspection(SnapshotStatus.CORRUPTED, failure.getMessage());
        }
    }

    public Optional<Snapshot> open(final ModpackFingerprint current, final ModpackFingerprint candidate,
            final boolean allowStale) throws IOException {
        if (!allowStale && !current.equals(candidate)) throw new IOException("Opening a stale snapshot requires consent");
        return store.loadLatest(candidate);
    }

    public Path publish(final ModpackDefinition pack, final ModpackFingerprint before, final Snapshot snapshot)
            throws IOException {
        final var environment = snapshot.environment();
        if (!Path.of(environment.sourceInstance()).toRealPath().equals(pack.gameDirectory().toRealPath())
                || !environment.minecraftVersion().equals(pack.minecraftVersion().value())
                || !environment.loader().equals(pack.loader().type().name())
                || !environment.loaderVersion().equals(pack.loader().version())) {
            throw new IOException("The runtime session belongs to a different Minecraft installation or loader");
        }
        final var loadedMods = snapshot.mods().stream().collect(Collectors.toMap(mod -> mod.id(), Function.identity()));
        for (final var mod : pack.mods()) {
            final var metadata = mod.metadata();
            final var loaded = loadedMods.get(metadata.id());
            final String version = metadata.version();
            if (loaded == null || (!version.equals("UNKNOWN") && !version.contains("${")
                    && !version.equals(loaded.version()))) {
                throw new IOException("Runtime mod does not match imported metadata: " + metadata.id());
            }
        }
        if (!before.equals(ModpackFingerprint.calculate(pack))) {
            throw new IOException("Modpack inputs changed during collection. Try again with the current installation.");
        }
        store.save(before, snapshot);
        return store.database(before);
    }
}
