package dev.recipeeditor.core.importing;

import java.nio.file.Path;
import java.util.List;
public record ModpackDefinition(Path selectedDirectory, Path gameDirectory, String structure,
        MinecraftVersion minecraftVersion, ModLoaderDefinition loader, List<ModScanResult> jars) {
    public ModpackDefinition { jars = List.copyOf(jars); }
    public List<ModDefinition> mods() { return jars.stream().flatMap(j -> j.mods().stream()).toList(); }
}
