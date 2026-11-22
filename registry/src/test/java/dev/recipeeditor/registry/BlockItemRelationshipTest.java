package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BlockItemRelationshipTest {
    static RegistryEntry runtime(final ResourceKind kind,final String id,final Map<String,String> metadata){return runtime(kind,id,metadata,"capture");}
    static RegistryEntry runtime(final ResourceKind kind,final String id,final Map<String,String> metadata,final String capture){
        return new RegistryEntry(ResourceLocation.parse(id),kind,id,"",Map.of(),List.of(new ResourceSource(SourceType.RUNTIME,List.of(),Path.of("snapshot.zip"),"snapshot/"+capture+"/"+id,"runtime")),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"runtime",ResourceVisual.unavailable(ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED,"Unknown"),metadata);
    }
    @Test void actualLinkMayUseDifferentNamespacesAndIds(){
        final var relationships=new BlockItemRelationships(new RegistryIndex(List.of(runtime(ResourceKind.ITEM,"test:placing_item",Map.of("blockItem","true","blockId","other:placed_block")),runtime(ResourceKind.BLOCK,"other:placed_block",Map.of("hasBlockItem","true")))));
        assertEquals(ResourceLocation.parse("other:placed_block"),relationships.block(ResourceLocation.parse("test:placing_item")).orElseThrow());assertEquals(List.of(ResourceLocation.parse("test:placing_item")),relationships.items(ResourceLocation.parse("other:placed_block")));
    }
    @Test void equalIdsAndOldSnapshotsNeverInventLinksOrAbsence(){
        final var id=ResourceLocation.parse("minecraft:stone");final var relationships=new BlockItemRelationships(new RegistryIndex(List.of(runtime(ResourceKind.ITEM,id.toString(),Map.of("blockItem","true")),runtime(ResourceKind.BLOCK,id.toString(),Map.of()))));
        assertTrue(relationships.isBlockItem(id));assertTrue(relationships.block(id).isEmpty());assertFalse(relationships.hasNoItem(id));
    }
    @Test void confirmedAbsenceIsNotInferredFromAnEmptyItemRegistry(){
        final var known=runtime(ResourceKind.BLOCK,"test:water",Map.of("hasBlockItem","false"));final var unknown=runtime(ResourceKind.BLOCK,"test:unknown",Map.of());
        final var relationships=new BlockItemRelationships(new RegistryIndex(List.of(known,unknown)));assertTrue(relationships.hasNoItem(known.id()));assertFalse(relationships.hasNoItem(unknown.id()));
    }
    @Test void conflictingAndDifferentCaptureLinksAreUnknown(){
        final var a=runtime(ResourceKind.BLOCK,"test:a",Map.of());final var b=runtime(ResourceKind.BLOCK,"test:b",Map.of());
        final var first=runtime(ResourceKind.ITEM,"test:item",Map.of("blockItem","true","blockId","test:a"));final var second=runtime(ResourceKind.ITEM,"test:item",Map.of("blockItem","true","blockId","test:b"));
        assertTrue(new BlockItemRelationships(new RegistryIndex(List.of(a,b,first,second))).block(first.id()).isEmpty());
        assertTrue(new BlockItemRelationships(new RegistryIndex(List.of(first,runtime(ResourceKind.BLOCK,"test:a",Map.of(),"another")))).block(first.id()).isEmpty());
    }
    @Test void contradictoryRuntimePresenceAndAbsenceRemainUnknown(){
        final var item=runtime(ResourceKind.ITEM,"test:item",Map.of("blockItem","true","blockId","test:block"));
        final var block=runtime(ResourceKind.BLOCK,"test:block",Map.of("hasBlockItem","false"));
        final var relationships=new BlockItemRelationships(new RegistryIndex(List.of(item,block)));
        assertTrue(relationships.block(item.id()).isEmpty());assertFalse(relationships.hasNoItem(block.id()));
    }
}
