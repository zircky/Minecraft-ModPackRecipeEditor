package dev.recipeeditor.registry;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TextureInheritanceTest {
    @Test void explicitGeneratedParentOverrideCannotBypassCycleDetection() {
        final var root=ResourceFixtures.document("assets/test/models/item/a.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"test:item/a\"}}");
        final var override=ResourceFixtures.document("assets/minecraft/models/item/generated.json","{\"parent\":\"test:item/a\"}");
        assertEquals(ResourceVisual.Status.INVALID_TEXTURE,new ModelTextureResolver(new ResourceScanResult(List.of(root,override),List.of(),List.of())).preview(root).status());
    }
    @Test void childVariablesOverrideParentAcrossNestedModels() {
        final var parent=ResourceFixtures.document("assets/test/models/item/base.json","{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"#top\",\"top\":\"#bottom\",\"bottom\":\"test:item/old\"}}");
        final var child=ResourceFixtures.document("assets/test/models/item/child.json","{\"parent\":\"test:item/base\",\"textures\":{\"bottom\":\"test:item/new\"}}");
        final var source=new ResourceSource(SourceType.MOD_JAR,List.of(),Path.of("a.jar"),"assets/test/textures/item/new.png","fixture");
        final var preview=new ModelTextureResolver(new ResourceScanResult(List.of(parent,child),List.of(source),List.of())).preview(child);
        assertEquals(source,preview.layers().getFirst().source());
    }
    @Test void modelAndTextureCyclesFailWithoutRecursionOverflow() {
        final var model=ResourceFixtures.document("assets/test/models/item/a.json","{\"parent\":\"test:item/a\"}");
        final var variable=ResourceFixtures.document("assets/test/models/item/b.json","{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"#a\",\"a\":\"#layer0\"}}");
        final var resolver=new ModelTextureResolver(new ResourceScanResult(List.of(model,variable),List.of(),List.of()));
        assertEquals(ResourceVisual.Status.INVALID_TEXTURE,resolver.preview(model).status());assertEquals(ResourceVisual.Status.INVALID_TEXTURE,resolver.preview(variable).status());
    }
}
