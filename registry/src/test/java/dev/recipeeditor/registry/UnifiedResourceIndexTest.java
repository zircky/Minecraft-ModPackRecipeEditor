package dev.recipeeditor.registry;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UnifiedResourceIndexTest {
    @Test void oneCardPerItemAndIndependentBlockIdentity(){
        final var item=BlockItemRelationshipTest.runtime(ResourceKind.ITEM,"test:a",Map.of("blockItem","true","blockId","test:a"));
        final var block=BlockItemRelationshipTest.runtime(ResourceKind.BLOCK,"test:a",Map.of("hasBlockItem","true"));
        final var noItem=BlockItemRelationshipTest.runtime(ResourceKind.BLOCK,"test:water",Map.of("hasBlockItem","false"));
        final var unknown=BlockItemRelationshipTest.runtime(ResourceKind.BLOCK,"test:unknown",Map.of());
        final var source=new RegistryIndex(List.of(item,item,block,noItem,unknown));final var unified=new UnifiedResourceIndex(source);
        assertEquals(List.of(item,noItem),unified.entries(UnifiedResourceIndex.Type.ALL));assertEquals(List.of(item),unified.entries(UnifiedResourceIndex.Type.BLOCK_ITEMS));assertEquals(3,unified.entries(UnifiedResourceIndex.Type.ALL_BLOCKS).size());assertEquals(5,source.entries().size());
        assertNotEquals(ResourceIdentity.of(item),ResourceIdentity.of(block));
    }
    @Test void conflictingDefinitionsPreserveSourcesAndSuppressArbitraryPreview(){
        final var a=BlockItemRelationshipTest.runtime(ResourceKind.ITEM,"test:a",Map.of("blockItem","true"));final var b=BlockItemRelationshipTest.runtime(ResourceKind.ITEM,"test:a",Map.of("blockItem","false"),"another");
        final var merged=new UnifiedResourceIndex(new RegistryIndex(List.of(a,b))).entries(UnifiedResourceIndex.Type.ALL).getFirst();
        assertEquals(2,merged.sources().size());assertEquals(ResourceVisual.Status.AMBIGUOUS_SOURCE,merged.visual().status());assertTrue(merged.metadata().isEmpty());
    }
}
