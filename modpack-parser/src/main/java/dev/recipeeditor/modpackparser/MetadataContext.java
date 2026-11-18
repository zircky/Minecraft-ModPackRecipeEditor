package dev.recipeeditor.modpackparser;

import java.util.Map;
public record MetadataContext(String metadataPath, Map<String, String> manifestAttributes) {
    public MetadataContext { manifestAttributes = Map.copyOf(manifestAttributes); }
}
