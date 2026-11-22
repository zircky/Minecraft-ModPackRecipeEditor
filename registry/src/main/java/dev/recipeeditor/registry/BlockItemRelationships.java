package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

/** Only explicit runtime metadata establishes relationships; equal IDs prove nothing. */
public final class BlockItemRelationships {
    private final Map<ResourceLocation,Set<ResourceLocation>> blocksByItem=new HashMap<>();
    private final Map<ResourceLocation,Set<ResourceLocation>> itemsByBlock=new HashMap<>();
    private final Set<ResourceLocation> blockItems=new HashSet<>(),withoutItems=new HashSet<>();
    public BlockItemRelationships(final RegistryIndex index) {
        final var blocks=index.entries(ResourceKind.BLOCK);
        final var grouped=blocks.stream().collect(java.util.stream.Collectors.groupingBy(RegistryEntry::id));
        final var itemDefinitions=index.entries(ResourceKind.ITEM).stream().collect(java.util.stream.Collectors.groupingBy(RegistryEntry::id));
        final var mentionedBlocks=index.entries(ResourceKind.ITEM).stream().filter(this::runtime).map(e->e.metadata().get("blockId")).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for(final var item:index.entries(ResourceKind.ITEM)) {
            if(!runtime(item)||!"true".equals(item.metadata().get("blockItem")))continue;
            final var definitions=itemDefinitions.get(item.id());
            if(definitions.stream().anyMatch(e->runtime(e)&&!"true".equals(e.metadata().get("blockItem"))))continue;
            blockItems.add(item.id());
            if(definitions.stream().filter(this::runtime).anyMatch(e->!Objects.equals(e.metadata().get("blockId"),item.metadata().get("blockId"))))continue;
            try {
                final String value=item.metadata().get("blockId");if(value==null)continue;
                final var id=ResourceLocation.parse(value);
                final var targets=grouped.getOrDefault(id,List.of());
                if(targets.stream().anyMatch(block->sameCapture(item,block)&&"false".equals(block.metadata().get("hasBlockItem"))))continue;
                if(targets.stream().noneMatch(block->sameCapture(item,block)))continue;
                blocksByItem.computeIfAbsent(item.id(),key->new HashSet<>()).add(id);
            } catch(IllegalArgumentException ignored) { /* Malformed optional metadata is not evidence. */ }
        }
        blocksByItem.forEach((item,ids)->{if(ids.size()==1)itemsByBlock.computeIfAbsent(ids.iterator().next(),key->new HashSet<>()).add(item);});
        grouped.forEach((id,definitions)->{
            if(!mentionedBlocks.contains(id.toString())&&!itemsByBlock.containsKey(id)&&definitions.stream().allMatch(block->runtime(block)&&"false".equals(block.metadata().get("hasBlockItem"))))withoutItems.add(id);
        });
    }
    public boolean isBlockItem(final ResourceLocation item){return blockItems.contains(item);}
    public Optional<ResourceLocation> block(final ResourceLocation item){final var ids=blocksByItem.getOrDefault(item,Set.of());return ids.size()==1?Optional.of(ids.iterator().next()):Optional.empty();}
    public List<ResourceLocation> items(final ResourceLocation block){return itemsByBlock.getOrDefault(block,Set.of()).stream().sorted().toList();}
    public boolean hasNoItem(final ResourceLocation block){return withoutItems.contains(block);}
    private boolean runtime(final RegistryEntry entry){return entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME;}
    private boolean sameCapture(final RegistryEntry item,final RegistryEntry block){
        if(!runtime(block))return false;
        return item.sources().stream().anyMatch(a->block.sources().stream().anyMatch(b->a.container().equals(b.container())&&capture(a).equals(capture(b))&&!capture(a).isEmpty()));
    }
    private String capture(final ResourceSource source){final var parts=source.internalPath().split("/",3);return parts.length==3&&parts[0].equals("snapshot")?parts[1]:"";}
}
