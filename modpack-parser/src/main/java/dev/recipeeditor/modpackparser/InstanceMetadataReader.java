package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.io.SafeFiles;

import com.fasterxml.jackson.databind.*;
import dev.recipeeditor.core.importing.*;
import java.nio.file.*;
import java.util.*;

/** Reads only explicit instance metadata and bounded version-profile directories. */
public final class InstanceMetadataReader {
    private final ObjectMapper mapper = new ObjectMapper();
    public MetadataEvidence read(final DetectionResult detected) {
        final var versions = new ArrayList<MetadataEvidence.VersionEvidence>();
        final var loaders = new ArrayList<MetadataEvidence.LoaderEvidence>();
        final var diagnostics = new ArrayList<ImportDiagnostic>();
        final var root = detected.selectedDirectory();
        final var files = new LinkedHashSet<Path>();
        for (final var base : List.of(root, detected.gameDirectory())) {
            for (final var name : List.of("mmc-pack.json", "minecraftinstance.json", "manifest.json", "modrinth.index.json", "version.json"))
                if (Files.exists(base.resolve(name))) files.add(base.resolve(name));
        }
        // Version directories are not used as an active profile: installations often contain several versions.
        for (final var path : files) {
            SafeFiles.checkCancelled();
            try {
                final var json = mapper.readTree(SafeFiles.read(root, path, 1024 * 1024));
                final String source = path.toString();
                switch (path.getFileName().toString()) {
                    case "mmc-pack.json" -> {
                        for (final var component : json.path("components")) {
                            final String uid = component.path("uid").asText();
                            final String version = component.path("version").asText();
                            if (uid.equals("net.minecraft")) addVersion(versions, version, source + "#components/net.minecraft");
                            else addLoader(loaders, uid, version, source + "#components/" + uid);
                        }
                        if (json.path("components").isArray() && versions.stream().anyMatch(v -> v.source().startsWith(source))
                                && loaders.stream().noneMatch(l -> l.source().startsWith(source)))
                            loaders.add(new MetadataEvidence.LoaderEvidence(ModLoaderType.VANILLA, "UNKNOWN", source + "#components"));
                    }
                    case "manifest.json" -> {
                        addVersion(versions, json.path("minecraft").path("version").asText(), source + "#minecraft.version");
                        for (final var loader : json.path("minecraft").path("modLoaders"))
                            parseLoaderId(loaders, loader.path("id").asText(), source + "#minecraft.modLoaders");
                    }
                    case "modrinth.index.json" -> {
                        if (!json.path("game").asText().equals("minecraft")) break;
                        final var dependencies = json.path("dependencies");
                        addVersion(versions, dependencies.path("minecraft").asText(), source + "#dependencies.minecraft");
                        for (final String loader : List.of("forge", "neoforge", "fabric-loader", "quilt-loader")) {
                            final var declared = dependencies.path(loader);
                            if (declared.isTextual()) addLoader(loaders, loader, declared.textValue(), source + "#dependencies." + loader);
                        }
                    }
                    case "minecraftinstance.json" -> {
                        addVersion(versions, json.path("gameVersion").asText(), source + "#gameVersion");
                        final var base = json.path("baseModLoader");
                        addVersion(versions, base.path("minecraftVersion").asText(), source + "#baseModLoader.minecraftVersion");
                        parseLoaderId(loaders, base.path("name").asText(), source + "#baseModLoader.name");
                        if (base.isMissingNode() || base.isNull()) {
                            if (!json.path("gameVersion").asText().isBlank()) loaders.add(new MetadataEvidence.LoaderEvidence(ModLoaderType.VANILLA,"UNKNOWN",source));
                        }
                    }
                    case "version.json" -> {
                        addVersion(versions, json.path("inheritsFrom").asText(), source + "#inheritsFrom");
                        if (json.path("inheritsFrom").isMissingNode() && json.has("assetIndex") && json.has("mainClass"))
                            addVersion(versions, json.path("id").asText(), source + "#id");
                        boolean found = false;
                        for (final var library : json.path("libraries")) {
                            final String[] parts = library.path("name").asText().split(":");
                            if (parts.length >= 3) {
                                final int before = loaders.size();
                                final String coordinate = parts[0] + ":" + parts[1];
                                final String minecraft = json.path("inheritsFrom").asText();
                                String version = parts[2];
                                // Forge profiles encode the game and the loader in the Maven version.
                                if (coordinate.equals("net.minecraftforge:forge") && !minecraft.isBlank()) {
                                    if (version.startsWith(minecraft + "-")) version = version.substring(minecraft.length() + 1);
                                    if (version.endsWith("-" + minecraft)) version = version.substring(0, version.length() - minecraft.length() - 1);
                                }
                                addLoader(loaders, coordinate, version, source + "#libraries");
                                found |= loaders.size() > before;
                            }
                        }
                        if (!found && json.path("mainClass").asText().equals("net.minecraft.client.main.Main"))
                            loaders.add(new MetadataEvidence.LoaderEvidence(ModLoaderType.VANILLA,"UNKNOWN",source + "#mainClass"));
                    }
                    default -> { }
                }
            } catch (Exception error) {
                if (error instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
                diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, path.toString(), "Cannot read instance metadata: " + error.getMessage()));
            }
        }
        return new MetadataEvidence(versions, loaders, diagnostics);
    }
    private void addVersion(final List<MetadataEvidence.VersionEvidence> out, final String version, final String source) {
        if (!version.isBlank()) out.add(new MetadataEvidence.VersionEvidence(version, source));
    }
    private void parseLoaderId(final List<MetadataEvidence.LoaderEvidence> out, final String id, final String source) {
        final String lower = id.toLowerCase(Locale.ROOT);
        for (final String prefix : List.of("fabric-loader", "quilt-loader", "neoforge", "forge", "fabric", "quilt")) {
            if (lower.startsWith(prefix + "-")) {
                addLoader(out, prefix, id.substring(prefix.length() + 1), source);
                return;
            }
        }
    }
    private void addLoader(final List<MetadataEvidence.LoaderEvidence> out, final String uid, final String version, final String source) {
        final var type = switch (uid.toLowerCase(Locale.ROOT)) {
            case "forge", "net.minecraftforge", "net.minecraftforge:forge" -> ModLoaderType.FORGE;
            case "neoforge", "net.neoforged", "net.neoforged.neoforge", "net.neoforged:neoforge" -> ModLoaderType.NEOFORGE;
            case "fabric", "fabric-loader", "net.fabricmc.fabric-loader", "net.fabricmc:fabric-loader" -> ModLoaderType.FABRIC;
            case "quilt", "quilt-loader", "org.quiltmc.quilt-loader", "org.quiltmc:quilt-loader" -> ModLoaderType.QUILT;
            default -> ModLoaderType.UNKNOWN;
        };
        if (type != ModLoaderType.UNKNOWN) out.add(new MetadataEvidence.LoaderEvidence(type, version.isBlank() ? "UNKNOWN" : version, source));
    }
}
