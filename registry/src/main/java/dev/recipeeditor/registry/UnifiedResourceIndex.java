package dev.recipeeditor.registry;

import java.util.*;

/** A presentation catalog. Item and Block registry identities remain independent. */
public final class UnifiedResourceIndex {
    public enum Type {
        ALL("All"), ITEMS("Items"), BLOCK_ITEMS("Block Items"), BLOCKS_WITHOUT_ITEM("Blocks without Item"), ALL_BLOCKS("All Blocks (technical)");
        private final String title;Type(final String title){this.title=title;}@Override public String toString(){return title;}
    }
    private final List<RegistryEntry> items,blocks;
    private final BlockItemRelationships relationships;
    public UnifiedResourceIndex(final RegistryIndex registry){
        relationships=new BlockItemRelationships(registry);
        items=unique(registry.entries(ResourceKind.ITEM));blocks=unique(registry.entries(ResourceKind.BLOCK));
    }
    public BlockItemRelationships relationships(){return relationships;}
    public List<RegistryEntry> entries(final Type type){return switch(type){
        case ALL->java.util.stream.Stream.concat(items.stream(),blocks.stream().filter(e->relationships.hasNoItem(e.id()))).toList();
        case ITEMS->items.stream().filter(e->!relationships.isBlockItem(e.id())).toList();
        case BLOCK_ITEMS->items.stream().filter(e->relationships.isBlockItem(e.id())).toList();
        case BLOCKS_WITHOUT_ITEM->blocks.stream().filter(e->relationships.hasNoItem(e.id())).toList();
        case ALL_BLOCKS->blocks;
    };}
    private List<RegistryEntry> unique(final List<RegistryEntry> entries){
        final var groups=new LinkedHashMap<ResourceIdentity,List<RegistryEntry>>();
        entries.forEach(e->groups.computeIfAbsent(ResourceIdentity.of(e),key->new ArrayList<>()).add(e));
        return groups.values().stream().map(this::combine).sorted(Comparator.comparing(RegistryEntry::id)).toList();
    }
    private RegistryEntry combine(final List<RegistryEntry> definitions){
        final var distinct=definitions.stream().distinct().toList();if(distinct.size()==1)return distinct.getFirst();
        final var preferred=distinct.stream().filter(e->e.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME).toList();
        if(preferred.size()==1)return preferred.getFirst();
        // Conflicting definitions retain every source, with no arbitrary preview or relationship.
        final var first=distinct.getFirst();
        return new RegistryEntry(first.id(),first.kind(),first.id().toString(),"",Map.of(),distinct.stream().flatMap(e->e.sources().stream()).distinct().toList(),List.of(),
                distinct.stream().allMatch(e->e.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME)?DiscoveryConfidence.CONFIRMED_BY_RUNTIME:DiscoveryConfidence.INFERRED_FROM_RESOURCES,
                "Conflicting definitions; inspect original evidence",ResourceVisual.unavailable(ResourceVisual.Status.AMBIGUOUS_SOURCE,"Multiple conflicting definitions"),Map.of());
    }
}
