package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.List;
public record TagDefinition(ResourceKind kind, ResourceLocation id, List<ResourceLocation> values, List<ResourceLocation> nestedTags,
        boolean replace, ResourceSource source) {
    public TagDefinition { values=List.copyOf(values); nestedTags=List.copyOf(nestedTags); }
}
