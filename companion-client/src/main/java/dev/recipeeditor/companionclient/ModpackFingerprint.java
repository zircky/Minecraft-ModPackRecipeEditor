package dev.recipeeditor.companionclient;

import dev.recipeeditor.core.importing.ModpackDefinition;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Content identity for an installed pack, independent of the absolute install directory. */
public record ModpackFingerprint(String value) {
    private static final List<String> INPUTS = List.of("mods", "datapacks", "kubejs", "resourcepacks",
            "config", "defaultconfigs", "serverconfig", "options.txt", "server.properties");

    public ModpackFingerprint {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a SHA-256 fingerprint");
        }
    }

    public static ModpackFingerprint calculate(final ModpackDefinition pack) throws IOException {
        final Path root = pack.gameDirectory().toRealPath();
        final var digest = sha256();
        field(digest, "mre-modpack-v1");
        field(digest, pack.minecraftVersion().value());
        field(digest, pack.loader().type().name());
        field(digest, pack.loader().version());
        final var mods = pack.mods().stream().map(mod -> mod.metadata())
                .sorted(Comparator.comparing(dev.recipeeditor.core.importing.ModMetadata::id)
                        .thenComparing(dev.recipeeditor.core.importing.ModMetadata::version)).toList();
        field(digest, Integer.toString(mods.size()));
        for (final var mod : mods) {
            field(digest, mod.id());
            field(digest, mod.version());
        }

        final var files = new TreeMap<String, Path>();
        for (final String input : INPUTS) collect(root, root.resolve(input), files);
        final Path saves = root.resolve("saves");
        if (Files.exists(saves, LinkOption.NOFOLLOW_LINKS)) {
            rejectLink(saves);
            try (var worlds = Files.newDirectoryStream(saves)) {
                for (final Path world : worlds) {
                    rejectLink(world);
                    collect(root, world.resolve("datapacks"), files);
                    collect(root, world.resolve("serverconfig"), files);
                }
            }
        }
        for (final var entry : files.entrySet()) {
            field(digest, entry.getKey());
            field(digest, hashFile(entry.getValue()));
        }
        return new ModpackFingerprint(HexFormat.of().formatHex(digest.digest()));
    }

    private static void collect(final Path root, final Path input, final Map<String, Path> files) throws IOException {
        if (Files.notExists(input, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(input, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(final Path directory, final BasicFileAttributes attributes) throws IOException {
                rejectLink(directory);
                // KubeJS log and cache files are generated during collection.
                if (directory.equals(root.resolve("kubejs/logs")) || directory.equals(root.resolve("kubejs/cache"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) throws IOException {
                if (file.equals(root.resolve("config/mre_companion/session.json"))) return FileVisitResult.CONTINUE;
                rejectLink(file);
                if (attributes.isRegularFile()) files.put(root.relativize(file).toString().replace('\\', '/'), file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String hashFile(final Path file) throws IOException {
        final var digest = sha256();
        if (file.getFileName().toString().equals("options.txt")) {
            // The ordered selected resource packs affect rendering. Other client preferences do not.
            try (var lines = Files.lines(file)) {
                lines.filter(line -> line.startsWith("resourcePacks:") || line.startsWith("incompatibleResourcePacks:"))
                        .sorted().forEach(line -> field(digest, line));
            } catch (java.io.UncheckedIOException failure) {
                throw failure.getCause();
            }
        } else {
            try (var in = new DigestInputStream(Files.newInputStream(file), digest)) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void rejectLink(final Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !path.toRealPath().equals(path.toAbsolutePath().normalize())) {
            throw new IOException("Redirected fingerprint input: " + path);
        }
    }

    private static void field(final MessageDigest digest, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
