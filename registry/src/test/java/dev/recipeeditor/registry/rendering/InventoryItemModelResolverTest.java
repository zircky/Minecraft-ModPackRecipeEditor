package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class InventoryItemModelResolverTest {
    static ResourceSource source(final String path){return new ResourceSource(SourceType.MOD_JAR,List.of("test"),Path.of("fixture.jar"),path,"fixture");}
    static InventoryItemModelResolver resolver(final Map<String,String> models){final var docs=models.entrySet().stream().map(e->new ResourceDocument(source("assets/"+e.getKey().replace(":","/models/")+".json"),e.getValue())).toList();return new InventoryItemModelResolver(new ResourceAssetCatalog(new ResourceScanResult(docs,List.of(source("assets/test/textures/block/a.png"),source("assets/test/textures/item/door.png")),List.of())));}
    static String element(){return "{\"textures\":{\"all\":\"test:block/a\"},\"elements\":[{\"from\":[4,0,4],\"to\":[12,16,12],\"faces\":{\"south\":{\"texture\":\"#all\"}}}]}";}
    @Test void actualItemParentWinsOverBlockModelAndNamespaceDoesNotChooseAnotherResource(){
        final var model=resolver(Map.of("test:item/fence","{\"parent\":\"test:block/fence_inventory\"}","test:block/fence_inventory",element(),"test:block/fence","{\"loader\":\"test:wrong_block_renderer\"}")).resolve(ResourceLocation.parse("test:fence"));
        assertEquals(InventoryItemModel.Kind.GEOMETRY,model.kind());assertEquals(4,model.geometry().elements().getFirst().from().x());assertTrue(model.sources().getFirst().internalPath().contains("models/item/fence"));
    }
    @Test void generatedDoorRemainsAnInventoryItemRatherThanDoorBlockGeometry(){
        final var model=resolver(Map.of("test:item/door","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"test:item/door\"}}","minecraft:item/generated","{\"parent\":\"builtin/generated\",\"gui_light\":\"front\"}","test:block/door",element())).resolve(ResourceLocation.parse("test:door"));
        assertEquals(InventoryItemModel.Kind.GENERATED_2D,model.kind());assertNull(model.geometry());assertEquals("front",model.guiLight());assertEquals("assets/test/textures/item/door.png",model.layers().layers().getFirst().source().internalPath());
    }
    @Test void missingItemModelDoesNotFallbackToAnArbitraryBlockTexture(){
        final var error=assertThrows(BlockModelException.class,()->resolver(Map.of("test:block/only",element())).resolve(ResourceLocation.parse("test:only")));assertEquals(BlockModelException.Code.MISSING_MODEL,error.code());
    }
    @Test void customRendererAndItemStackPredicatesNeedClientRatherThanClaimStaticSuccess(){
        for(final var json:List.of("{\"loader\":\"test:dynamic\"}","{\"overrides\":[{\"predicate\":{\"test:nbt\":1},\"model\":\"test:block/a\"}]}"))assertEquals(BlockModelException.Code.UNSUPPORTED_RENDERER,assertThrows(BlockModelException.class,()->resolver(Map.of("test:item/dynamic",json)).resolve(ResourceLocation.parse("test:dynamic"))).code());
    }
}
