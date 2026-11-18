package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
public final class RegistryIndex {
    private final List<RegistryEntry> entries;
    private final Map<ResourceKind,List<RegistryEntry>> byKind;
    private final Map<ResourceLocation,List<RegistryEntry>> byId;
    public RegistryIndex(final List<RegistryEntry> entries) {
        this.entries=List.copyOf(entries);
        byKind=entries.stream().collect(java.util.stream.Collectors.groupingBy(RegistryEntry::kind));
        byId=entries.stream().collect(java.util.stream.Collectors.groupingBy(RegistryEntry::id));
    }
    public List<RegistryEntry> entries() { return entries; }
    public List<RegistryEntry> entries(final ResourceKind kind) { return List.copyOf(byKind.getOrDefault(kind,List.of())); }
    public List<RegistryEntry> find(final ResourceLocation id) { return List.copyOf(byId.getOrDefault(id,List.of())); }
}
