package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.io.SafeFiles;

import dev.recipeeditor.core.importing.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ModpackDetector {
    public DetectionResult detect(final Path directory) throws IOException {
        final var root = directory.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Select a directory");
        final var diagnostics = new ArrayList<ImportDiagnostic>();
        final var candidates = List.of(root, root.resolve(".minecraft"), root.resolve("minecraft"));
        for (final var candidate : candidates) {
            if (!Files.exists(candidate)) continue;
            try {
                final var game = SafeFiles.inside(root, candidate);
                final boolean instance = marker(root, "mmc-pack.json", diagnostics);
                final boolean curse = marker(root, "minecraftinstance.json",diagnostics) | marker(root, "manifest.json",diagnostics);
                final boolean modrinth = marker(root, "modrinth.index.json", diagnostics);
                final boolean vanilla = marker(root, "version.json", diagnostics);
                final boolean explicit = instance || curse || modrinth || vanilla;
                final int signals = (dir(root, game, "config") ? 1 : 0) + (dir(root, game, "resourcepacks") ? 1 : 0)
                        + (dir(root, game, "saves") ? 1 : 0) + (fileAt(root, game.resolve("options.txt")) ? 1 : 0)
                        + (dir(root, game, "versions") ? 1 : 0);
                if (candidate.equals(root) && explicit && !dir(root, game, "mods")
                        && candidates.stream().skip(1).anyMatch(Files::isDirectory)) continue;
                if (explicit || signals >= 2 || (signals >= 1 && dir(root, game, "mods"))) {
                    final String structure = instance ? "MultiMC / Prism Launcher" : curse ? "CurseForge"
                            : modrinth ? "Modrinth" : "Minecraft directory";
                    return new DetectionResult(root, game, structure,
                            explicit ? DetectionResult.Confidence.HIGH : DetectionResult.Confidence.MEDIUM, diagnostics);
                }
            } catch (IOException error) {
                diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, candidate.toString(), error.getMessage()));
            }
        }
        diagnostics.add(new ImportDiagnostic(ScanStatus.ERROR, root.toString(),
                "No recognizable Minecraft structure. A mods folder alone is insufficient. Select an individual instance, not the instances collection."));
        return new DetectionResult(root, root, "UNKNOWN", DetectionResult.Confidence.NONE, diagnostics);
    }
    private boolean marker(final Path root, final String name, final List<ImportDiagnostic> diagnostics) {
        final var path = root.resolve(name);
        if (!Files.exists(path)) return false;
        try {
            final var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(SafeFiles.read(root,path,1024*1024));
            final boolean recognized = switch (name) {
                case "mmc-pack.json" -> {
                    boolean minecraft = false;
                    for (final var component : json.path("components"))
                        minecraft |= component.path("uid").asText().equals("net.minecraft") && !component.path("version").asText().isBlank();
                    yield minecraft;
                }
                case "minecraftinstance.json" -> !json.path("gameVersion").asText().isBlank()
                        || !json.path("baseModLoader").path("minecraftVersion").asText().isBlank();
                case "manifest.json" -> !json.path("minecraft").path("version").asText().isBlank();
                case "modrinth.index.json" -> json.path("game").asText().equals("minecraft")
                        && !json.path("dependencies").path("minecraft").asText().isBlank();
                case "version.json" -> !json.path("inheritsFrom").asText().isBlank()
                        || (json.has("assetIndex") && json.has("mainClass") && !json.path("id").asText().isBlank());
                default -> false;
            };
            if (!recognized) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path.toString(),"Unrecognized instance metadata structure"));
            return recognized;
        } catch (IOException error) {
            diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path.toString(),"Cannot read instance metadata: " + error.getMessage()));
            return false;
        }
    }
    private boolean fileAt(final Path root, final Path path) throws IOException {
        return Files.exists(path) && Files.isRegularFile(SafeFiles.inside(root, path));
    }
    private boolean dir(final Path root, final Path game, final String name) throws IOException {
        final var path = game.resolve(name);
        return Files.exists(path) && Files.isDirectory(SafeFiles.inside(root, path));
    }
}
