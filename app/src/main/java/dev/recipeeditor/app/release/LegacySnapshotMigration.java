package dev.recipeeditor.app.release;

import dev.recipeeditor.companionclient.RuntimeSnapshotService;
import dev.recipeeditor.core.ApplicationDirectories;
import java.io.IOException;
import java.nio.file.*;

/** Explicit maintenance operation. Retains legacy data and never replaces an existing destination. */
public final class LegacySnapshotMigration {
    private final RuntimeSnapshotService snapshots = new RuntimeSnapshotService();

    public int migrate(final ApplicationDirectories directories) throws IOException {
        final var source = directories.legacy().resolve("runtime-snapshots");
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) return 0;
        directories.create();
        int copied = 0;
        try (final var files = Files.list(source)) {
            final var candidates = files.filter(p -> p.getFileName().toString().endsWith(".mresnapshot")).limit(1001).toList();
            if (candidates.size() > 1000) throw new IOException("Legacy snapshot migration limit exceeded");
            for (final var file : candidates) {
                final var destination = directories.snapshots().resolve(file.getFileName());
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) continue;
                snapshots.load(file); // Existing checksums, schema and path checks are reused.
                final var temporary = Files.createTempFile(directories.snapshots(), ".migration-", ".tmp");
                try {
                    Files.copy(file, temporary, StandardCopyOption.REPLACE_EXISTING);
                    snapshots.load(temporary);
                    Files.move(temporary, destination); // No REPLACE_EXISTING: a concurrent destination is protected.
                    copied++;
                } finally { Files.deleteIfExists(temporary); }
            }
        }
        return copied;
    }
}
