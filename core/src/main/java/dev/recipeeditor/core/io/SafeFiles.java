package dev.recipeeditor.core.io;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.CancellationException;

public final class SafeFiles {
    private SafeFiles() { }
    public static Path inside(final Path root, final Path path) throws IOException {
        final var real = path.toRealPath();
        if (!real.startsWith(root.toRealPath())) throw new IOException("Path escapes selected directory: " + path);
        return real;
    }
    public static byte[] read(final InputStream input, final int limit) throws IOException {
        final var output = new ByteArrayOutputStream();
        final var buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkCancelled();
            if (output.size() + count > limit) throw new IOException("Metadata exceeds byte limit " + limit);
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    public static byte[] read(final Path root, final Path path, final int limit) throws IOException {
        final var safe = inside(root, path);
        if (!Files.isRegularFile(safe) || Files.size(safe) > limit) throw new IOException("Invalid or oversized metadata: " + path);
        try (final var input = Files.newInputStream(safe)) { return read(input, limit); }
    }
    public static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Import cancelled");
    }
}
