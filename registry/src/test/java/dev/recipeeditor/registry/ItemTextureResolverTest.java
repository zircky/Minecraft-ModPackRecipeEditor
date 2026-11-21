package dev.recipeeditor.registry;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ItemTextureResolverTest {
    @Test void generatedLayersAndBlockItemReferences() {
        final var item=ResourceFixtures.document("assets/test/models/item/tool.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"test:item/base\",\"layer1\":\"test:item/overlay\"}}");
        final var block=ResourceFixtures.document("assets/test/models/block/metal.json","{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"test:item/base\"}}");
        final var blockItem=ResourceFixtures.document("assets/test/models/item/metal.json","{\"parent\":\"test:block/metal\"}");
        final var scan=new ResourceScanResult(List.of(item,block,blockItem),List.of(texture("base","a.jar"),texture("overlay","a.jar")),List.of());
        final var resolver=new ModelTextureResolver(scan);
        assertEquals(2,resolver.preview(item).layers().size());assertEquals(ResourceVisual.Status.READY,resolver.preview(item).status());
        assertEquals(ResourceVisual.Status.TEXTURE_SAMPLE,resolver.preview(blockItem).status());
    }
    @Test void missingAndAmbiguousTexturesNeverPickRandomJar() {
        final var item=ResourceFixtures.document("assets/test/models/item/a.json","{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"test:item/base\"}}");
        assertEquals(ResourceVisual.Status.TEXTURE_NOT_FOUND,new ModelTextureResolver(new ResourceScanResult(List.of(item),List.of(),List.of())).preview(item).status());
        final var scan=new ResourceScanResult(List.of(item),List.of(texture("base","a.jar"),texture("base","b.jar")),List.of());
        assertEquals(ResourceVisual.Status.AMBIGUOUS_SOURCE,new ModelTextureResolver(scan).preview(item).status());
        assertTrue(new ModelTextureResolver(scan).preview(item).layers().isEmpty());
    }
    private ResourceSource texture(final String name,final String jar) { return new ResourceSource(SourceType.MOD_JAR,List.of("test"),Path.of(jar),"assets/test/textures/item/"+name+".png","fixture"); }
}
