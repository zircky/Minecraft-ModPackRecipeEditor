package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ResourceIndexersTest {
    @Test void itemBlockAndFluidEvidenceRemainsStatic() {
        final var documents=List.of(ResourceFixtures.document("assets/test/models/item/a.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"test:item/a\"}}"),
                ResourceFixtures.document("assets/test/blockstates/b.json","{\"multipart\":[]}"),
                ResourceFixtures.document("data/test/tags/fluids/water.json","{\"values\":[\"test:water\"]}"));
        final var texture=new ResourceSource(SourceType.MOD_JAR,List.of("test"),java.nio.file.Path.of("test.jar"),"assets/test/textures/item/a.png","test");
        final var scan=new ResourceScanResult(documents,List.of(texture),List.of()); final var tags=new TagIndex(documents);
        final var localization=new LocalizationService(documents); final var diagnostics=new ArrayList<dev.recipeeditor.core.importing.ImportDiagnostic>();
        final var items=new ItemResourceIndexer().index(scan,localization,tags,"en_us",diagnostics,List.of(new ResourceReference(ResourceKind.ITEM,ResourceLocation.parse("test:a"),ResourceFixtures.document("data/test/recipes/evidence.json","{}").source())));
        final var blocks=new BlockResourceIndexer().index(scan,localization,tags,"en_us",diagnostics);
        final var fluids=new FluidResourceIndexer().index(scan,localization,tags,"en_us",diagnostics);
        assertEquals(1,items.size()); assertEquals(1,items.getFirst().textures().size());
        assertEquals(1,blocks.size()); assertTrue(blocks.getFirst().textures().isEmpty());
        assertEquals("test:water",fluids.getFirst().id().toString());
        assertTrue(java.util.stream.Stream.of(items,blocks,fluids).flatMap(List::stream).allMatch(e->e.confidence()!=DiscoveryConfidence.CONFIRMED_BY_RUNTIME));
        assertFalse(items.stream().anyMatch(e->e.id().equals(ResourceLocation.parse("test:b"))));
    }
    @Test void texturesAloneDoNotCreateFluids() {
        final var scan=new ResourceScanResult(List.of(),List.of(new ResourceSource(SourceType.MOD_JAR,List.of(),java.nio.file.Path.of("test.jar"),"assets/test/textures/block/liquid.png","test")),List.of());
        assertTrue(new FluidResourceIndexer().index(scan,new LocalizationService(List.of()),new TagIndex(List.of()),"en_us",new ArrayList<>()).isEmpty());
    }
    @Test void tagCyclesDoNotRecurseForever() {
        final var tags=new TagIndex(List.of(ResourceFixtures.document("data/test/tags/items/a.json","{\"values\":[\"#test:b\",\"test:item\"]}"),ResourceFixtures.document("data/test/tags/items/b.json","{\"values\":[\"#test:a\"]}")));
        assertTrue(tags.members(ResourceKind.ITEM,ResourceLocation.parse("test:b")).contains(ResourceLocation.parse("test:item")));
    }
    @Test void complexBlockstateLabelsRepresentativeSampleAsUnsupportedGeometry() {
        final var docs=List.of(ResourceFixtures.document("assets/test/models/block/a.json","{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"test:block/a\"}}"),
                ResourceFixtures.document("assets/test/blockstates/a.json","{\"multipart\":[{\"apply\":{\"model\":\"test:block/a\"}}]}"));
        final var texture=new ResourceSource(SourceType.MOD_JAR,List.of("test"),java.nio.file.Path.of("test.jar"),"assets/test/textures/block/a.png","test");
        final var scan=new ResourceScanResult(docs,List.of(texture),List.of());
        final var entries=new BlockResourceIndexer().index(scan,new LocalizationService(docs),new TagIndex(docs),"en_us",new ArrayList<>());
        assertEquals(1,entries.size()); assertEquals(ResourceVisual.Status.RENDERING_UNSUPPORTED,entries.getFirst().visual().status());
        assertEquals(1,entries.getFirst().textures().size());
    }
}
