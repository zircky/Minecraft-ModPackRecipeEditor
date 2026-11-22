package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("performance")
final class UnifiedResourcePerformanceTest {
    @Test void largeCatalogBuildsAndLinksWithoutComparingEveryItemToEveryBlock(){
        assertTimeout(Duration.ofSeconds(30),()->{
            final var entries=new ArrayList<RegistryEntry>();
            for(int i=0;i<20_000;i++)entries.add(entry(ResourceKind.BLOCK,"block_"+i,Map.of("hasBlockItem","true")));
            for(int i=0;i<50_000;i++)entries.add(entry(ResourceKind.ITEM,"item_"+i,i<20_000?Map.of("blockItem","true","blockId","test:block_"+i):Map.of("blockItem","false")));
            final var unified=new UnifiedResourceIndex(new RegistryIndex(entries));assertEquals(50_000,unified.entries(UnifiedResourceIndex.Type.ALL).size());assertEquals(20_000,unified.entries(UnifiedResourceIndex.Type.BLOCK_ITEMS).size());assertEquals(20_000,unified.entries(UnifiedResourceIndex.Type.ALL_BLOCKS).size());
            assertEquals(ResourceLocation.parse("test:block_19999"),unified.relationships().block(ResourceLocation.parse("test:item_19999")).orElseThrow());
        });
    }
    private RegistryEntry entry(final ResourceKind kind,final String path,final Map<String,String> metadata){
        final var id=new ResourceLocation("test",path);return new RegistryEntry(id,kind,id.toString(),"",Map.of(),List.of(new ResourceSource(SourceType.RUNTIME,List.of(),Path.of("snapshot.zip"),"snapshot/performance/"+id,"runtime")),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"runtime",ResourceVisual.unavailable(ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED,"Unknown"),metadata);
    }
}
