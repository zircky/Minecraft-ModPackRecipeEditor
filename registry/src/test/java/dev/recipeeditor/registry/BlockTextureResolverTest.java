package dev.recipeeditor.registry;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BlockTextureResolverTest {
    @Test void multipartAndWeightedVariantsAreRepresentativeSamples() {
        final var model=ResourceFixtures.document("assets/test/models/block/a.json","{\"elements\":[],\"textures\":{\"particle\":\"#side\",\"side\":\"test:block/metal\"}}");
        final var multipart=ResourceFixtures.document("assets/test/blockstates/a.json","{\"multipart\":[{\"apply\":[{\"model\":\"test:block/a\"}]}]}");
        final var variant=ResourceFixtures.document("assets/test/blockstates/b.json","{\"variants\":{\"\":[{\"model\":\"test:block/a\"}]}}");
        final var texture=new ResourceSource(SourceType.MOD_JAR,List.of(),Path.of("a.jar"),"assets/test/textures/block/metal.png","fixture");
        final var resolver=new ModelTextureResolver(new ResourceScanResult(List.of(model,multipart,variant),List.of(texture),List.of()));
        for(final var document:List.of(multipart,variant)) { final var visual=resolver.preview(document);assertEquals(ResourceVisual.Status.RENDERING_UNSUPPORTED,visual.status());assertEquals(1,visual.layers().size()); }
    }
    @Test void customRendererWithNoTexturesHasExplicitStatus() {
        final var model=ResourceFixtures.document("assets/test/models/block/a.json","{\"loader\":\"test:dynamic\"}");
        assertEquals(ResourceVisual.Status.UNSUPPORTED_RENDERER,new ModelTextureResolver(new ResourceScanResult(List.of(model),List.of(),List.of())).preview(model).status());
    }
}
