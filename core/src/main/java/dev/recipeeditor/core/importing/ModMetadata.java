package dev.recipeeditor.core.importing;

import java.util.List;
public record ModMetadata(String id, String displayName, String version, String description,
        List<String> authors, List<ModDependency> dependencies, ModLoaderType loaderCompatibility,
        String metadataSource, String rawMetadata) {
    public ModMetadata { authors = List.copyOf(authors); dependencies = List.copyOf(dependencies); }
}
