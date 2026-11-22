package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
public final class RegistryIndex {
    private final List<RegistryEntry> entries;
    private final Map<ResourceKind,List<RegistryEntry>> byKind;
    private final Map<ResourceLocation,List<RegistryEntry>> byId;
    private final ResourceScanResult assets;
    private final ResourceAssetCatalog assetCatalog;
    private final List<MaterialDefinition> materials;
    public RegistryIndex(final List<RegistryEntry> entries) {
        this(entries,new ResourceScanResult(List.of(),List.of(),List.of()));
    }
    public RegistryIndex(final List<RegistryEntry> entries, final ResourceScanResult assets) {
        this(entries,assets,List.of());
    }
    public RegistryIndex(final List<RegistryEntry> entries, final ResourceScanResult assets,final List<MaterialDefinition> materials) {
        this.entries=List.copyOf(entries);
        this.assets=Objects.requireNonNull(assets);
        assetCatalog=new ResourceAssetCatalog(assets);
        this.materials=List.copyOf(materials);
        byKind=entries.stream().collect(java.util.stream.Collectors.groupingBy(RegistryEntry::kind));
        byId=entries.stream().collect(java.util.stream.Collectors.groupingBy(RegistryEntry::id));
    }
    public List<RegistryEntry> entries() { return entries; }
    public ResourceScanResult assets() { return assets; }
    public ResourceAssetCatalog assetCatalog(){return assetCatalog;}
    public List<MaterialDefinition> materials() { return materials; }
    public List<RegistryEntry> entries(final ResourceKind kind) { return List.copyOf(byKind.getOrDefault(kind,List.of())); }
    public List<RegistryEntry> find(final ResourceLocation id) { return List.copyOf(byId.getOrDefault(id,List.of())); }
}
