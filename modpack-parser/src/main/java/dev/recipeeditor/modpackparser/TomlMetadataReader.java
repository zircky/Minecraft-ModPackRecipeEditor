package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.io.SafeFiles;

import dev.recipeeditor.core.importing.*;
import org.tomlj.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class TomlMetadataReader {
    ModMetadataParseResult parse(final InputStream input, final MetadataContext context, final ModLoaderType defaultLoader) {
        try {
            final String raw = new String(SafeFiles.read(input, 512 * 1024), StandardCharsets.UTF_8);
            final var document = Toml.parse(raw);
            if (document.hasErrors()) throw new IOException(document.errors().toString());
            final var mods = document.getArray("mods");
            if (mods == null || mods.isEmpty()) throw new IOException("Missing [[mods]] entries");
            final var result = new ArrayList<ModMetadata>();
            final var diagnostics = new ArrayList<ImportDiagnostic>();
            if (document.get("dependencies") instanceof TomlArray)
                diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,context.metadataPath(),
                        "Nonstandard unscoped [[dependencies]] entries are preserved in raw metadata but not evaluated"));
            for (int index = 0; index < mods.size(); index++) {
                final var mod = mods.getTable(index);
                final String id = text(mod, "modId", "");
                final var dependencies = dependencies(document, id);
                final var loader = dependencies.stream().anyMatch(d -> d.targetId().equals("neoforge"))
                        ? ModLoaderType.NEOFORGE : defaultLoader;
                String version = text(mod, "version", "UNKNOWN");
                if (version.equals("${file.jarVersion}")) version = context.manifestAttributes().getOrDefault("Implementation-Version", version);
                final var metadata = new ModMetadata(id, text(mod, "displayName", id), version,
                        text(mod,"description",""), authors(mod), dependencies, loader, context.metadataPath(), raw);
                ParserSupport.validate(metadata, diagnostics);
                result.add(metadata);
            }
            if (document.contains("jarjar")) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,context.metadataPath(),"Nested JAR dependencies are not scanned"));
            return new ModMetadataParseResult(result, diagnostics);
        } catch (Exception error) {
            if (error instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
            return ParserSupport.failure(context.metadataPath(), error);
        }
    }
    private List<String> authors(final TomlTable table) {
        final Object value = table.get("authors");
        if (value instanceof String text) return text.isBlank() ? List.of() : List.of(text);
        if (value instanceof TomlArray array) return array.toList().stream().map(Object::toString).toList();
        return List.of();
    }
    private List<ModDependency> dependencies(final TomlTable document, final String id) {
        if (!(document.get("dependencies") instanceof TomlTable table)) return List.of();
        final Object value = table.get(List.of(id));
        if (!(value instanceof TomlArray array)) return List.of();
        final var result = new ArrayList<ModDependency>();
        for (int i = 0; i < array.size(); i++) {
            final var dependency = array.getTable(i);
            final String type = text(dependency,"type", "");
            final var kind = switch (type) {
                case "required" -> DependencyKind.REQUIRED;
                case "optional", "discouraged" -> DependencyKind.OPTIONAL;
                case "incompatible" -> DependencyKind.INCOMPATIBLE;
                case "" -> Boolean.FALSE.equals(dependency.getBoolean("mandatory")) ? DependencyKind.OPTIONAL : DependencyKind.REQUIRED;
                default -> DependencyKind.UNKNOWN;
            };
            result.add(new ModDependency(text(dependency,"modId",""), kind, text(dependency,"versionRange",""), dependency.toMap().toString()));
        }
        return result;
    }
    private String text(final TomlTable table, final String key, final String fallback) {
        final Object value = table.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
