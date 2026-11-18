package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.io.SafeFiles;

import com.fasterxml.jackson.databind.*;
import dev.recipeeditor.core.importing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class JsonMetadataReader {
    private final ObjectMapper mapper = new ObjectMapper();
    ModMetadataParseResult parse(final InputStream input, final MetadataContext context, final boolean quilt) {
        try {
            final String raw = new String(SafeFiles.read(input, 512 * 1024), StandardCharsets.UTF_8);
            final var document = mapper.readTree(raw);
            if (!document.isObject()) throw new IOException("Expected metadata object");
            final int schema = document.path(quilt ? "schema_version" : "schemaVersion").asInt(-1);
            if (schema != 1) throw new IOException("Unsupported metadata schema: " + schema);
            final var loader = quilt ? document.path("quilt_loader") : document;
            final var display = quilt ? loader.path("metadata") : loader;
            final var dependencies = new ArrayList<ModDependency>();
            final var diagnostics = new ArrayList<ImportDiagnostic>();
            if (quilt) {
                quiltDependencies(loader.path("depends"), DependencyKind.REQUIRED, dependencies, diagnostics, context.metadataPath());
                quiltDependencies(loader.path("breaks"), DependencyKind.INCOMPATIBLE, dependencies, diagnostics, context.metadataPath());
            } else {
                for (final var key : List.of("depends", "recommends", "suggests", "breaks", "conflicts")) {
                    final var kind = key.equals("depends") ? DependencyKind.REQUIRED : key.equals("breaks") || key.equals("conflicts")
                            ? DependencyKind.INCOMPATIBLE : DependencyKind.OPTIONAL;
                    final var fields = loader.path(key).fields();
                    while (fields.hasNext()) {
                        final var field = fields.next();
                        dependencies.add(new ModDependency(field.getKey(),kind, constraint(field.getValue()),field.getValue().toString()));
                    }
                }
            }
            final var authors = new ArrayList<String>();
            if (quilt) display.path("contributors").fieldNames().forEachRemaining(authors::add);
            else for (final var author : display.path("authors")) {
                final String name = author.isTextual() ? author.asText() : author.path("name").asText();
                if (!name.isBlank()) authors.add(name);
            }
            final String id = loader.path("id").asText();
            final var mod = new ModMetadata(id, display.path("name").asText(id), loader.path("version").asText("UNKNOWN"),
                    display.path("description").asText(), authors, dependencies,
                    quilt ? ModLoaderType.QUILT : ModLoaderType.FABRIC, context.metadataPath(),raw);
            ParserSupport.validate(mod, diagnostics);
            if (loader.has("jars") || loader.has("provides")) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,context.metadataPath(),
                    "Nested JARs and provided aliases are preserved in raw metadata but not resolved"));
            return new ModMetadataParseResult(List.of(mod), diagnostics);
        } catch (Exception error) {
            if (error instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
            return ParserSupport.failure(context.metadataPath(),error);
        }
    }
    private String constraint(final JsonNode node) { return node.isTextual() ? node.asText() : node.toString(); }
    private void quiltDependencies(final JsonNode array, final DependencyKind kind, final List<ModDependency> result,
            final List<ImportDiagnostic> diagnostics, final String source) {
        for (final var node : array) {
            if (node.isTextual()) result.add(new ModDependency(node.asText(),kind,"",node.toString()));
            else if (node.isObject() && node.has("id") && !node.has("unless"))
                result.add(new ModDependency(node.path("id").asText(),node.path("optional").asBoolean() ? DependencyKind.OPTIONAL : kind,
                        node.has("versions") ? constraint(node.path("versions")) : "",node.toString()));
            else {
                result.add(new ModDependency("",DependencyKind.UNKNOWN,"",node.toString()));
                diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,source,"Complex Quilt dependency is preserved but not evaluated: " + node));
            }
        }
    }
}
