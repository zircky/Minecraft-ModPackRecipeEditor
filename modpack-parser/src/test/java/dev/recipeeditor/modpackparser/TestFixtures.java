package dev.recipeeditor.modpackparser;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;

final class TestFixtures {
    private TestFixtures() { }
    static Path write(final Path root, final String path, final String content) throws IOException {
        final var file = root.resolve(path); Files.createDirectories(file.getParent()); return Files.writeString(file,content);
    }
    static Path jar(final Path root, final String name, final Map<String,String> entries) throws IOException {
        final var file = root.resolve(name); Files.createDirectories(file.getParent());
        try (final var output = new JarOutputStream(Files.newOutputStream(file))) {
            for (final var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey())); output.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); output.closeEntry();
            }
        }
        return file;
    }
    static String forge(final String id) { return "[[mods]]\nmodId='" + id + "'\nversion='1.0'\n"; }
    static String fabric() { return "{\"schemaVersion\":1,\"id\":\"sample\",\"version\":\"1\",\"name\":\"Sample\"}"; }
    static MetadataEvidence evidence(final Path root) throws IOException {
        return new InstanceMetadataReader().read(new DetectionResult(root,root,"test",DetectionResult.Confidence.HIGH,List.of()));
    }
}
