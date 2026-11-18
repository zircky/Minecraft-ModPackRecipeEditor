package dev.recipeeditor.core.importing;

import java.util.List;
public record ModLoaderDefinition(ModLoaderType type, String version, List<String> sources) {
    public ModLoaderDefinition { sources = List.copyOf(sources); }
}
