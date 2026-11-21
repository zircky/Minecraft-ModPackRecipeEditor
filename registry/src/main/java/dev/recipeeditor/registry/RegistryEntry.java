package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
public record RegistryEntry(ResourceLocation id, ResourceKind kind, String displayName, String localizationKey,
        Map<String,String> localizedNames, List<ResourceSource> sources, List<ResourceSource> textures,
        DiscoveryConfidence confidence, String discoveryMethod, ResourceVisual visual, Map<String,String> metadata) {
    public RegistryEntry(final ResourceLocation id, final ResourceKind kind, final String displayName, final String localizationKey,
            final Map<String,String> localizedNames, final List<ResourceSource> sources, final List<ResourceSource> textures,
            final DiscoveryConfidence confidence, final String discoveryMethod) {
        this(id,kind,displayName,localizationKey,localizedNames,sources,textures,confidence,discoveryMethod,ResourceVisual.legacy(textures),Map.of());
    }
    public RegistryEntry {
        Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(displayName);
        localizedNames=Map.copyOf(localizedNames); sources=List.copyOf(sources); textures=List.copyOf(textures);
        Objects.requireNonNull(visual); metadata=Map.copyOf(metadata);
        if (confidence==DiscoveryConfidence.CONFIRMED_BY_RUNTIME && sources.stream().anyMatch(source->source.type()!=SourceType.RUNTIME))
            throw new IllegalArgumentException("Static evidence cannot assert runtime registration");
    }
}
