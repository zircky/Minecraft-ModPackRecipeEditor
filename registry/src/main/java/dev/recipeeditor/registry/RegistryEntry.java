package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
public record RegistryEntry(ResourceLocation id, ResourceKind kind, String displayName, String localizationKey,
        Map<String,String> localizedNames, List<ResourceSource> sources, List<ResourceSource> textures,
        DiscoveryConfidence confidence, String discoveryMethod) {
    public RegistryEntry {
        Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(displayName);
        localizedNames=Map.copyOf(localizedNames); sources=List.copyOf(sources); textures=List.copyOf(textures);
        if (confidence==DiscoveryConfidence.CONFIRMED_BY_RUNTIME && sources.stream().anyMatch(source->source.type()!=SourceType.RUNTIME))
            throw new IllegalArgumentException("Static evidence cannot assert runtime registration");
    }
}
