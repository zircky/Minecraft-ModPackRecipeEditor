package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.recipeeditor.registry.rendering.BlockModelException.Code.*;

final class BlockModelParserTest {
    private ResourceSource source(final String path){return new ResourceSource(SourceType.MOD_JAR,List.of("test"),Path.of("fixture.jar"),path,"test fixture");}
    private BlockModelParser parser(final Map<String,String> definitions){
        final var docs=definitions.entrySet().stream().map(e->new ResourceDocument(source("assets/test/models/"+e.getKey()+".json"),e.getValue())).toList();
        return new BlockModelParser(new ResourceAssetCatalog(new ResourceScanResult(docs,List.of(source("assets/test/textures/block/a.png"),source("assets/test/textures/block/b.png")),List.of())));
    }
    @Test void inheritsGeometryButChildOverridesTextureVariablesAndKeepsEveryParentSource(){
        final var parser=parser(Map.of("block/base","{\"textures\":{\"all\":\"test:block/a\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"north\":{\"texture\":\"#all\",\"cullface\":\"north\"}}}]}","block/slab","{\"parent\":\"test:block/base\",\"textures\":{\"all\":\"#side\",\"side\":\"test:block/b\"}}"));
        final var model=parser.parse(ResourceLocation.parse("test:block/slab"));final var element=model.elements().getFirst();final var face=element.faces().get(BlockGeometryModel.Direction.NORTH);
        assertEquals(8,element.to().y());assertEquals(ResourceLocation.parse("test:block/b"),face.texture());assertEquals(new BlockGeometryModel.UV(0,8,16,16),face.uv());assertEquals(BlockGeometryModel.Direction.NORTH,face.cullface());assertEquals(2,model.sources().size());
    }
    @Test void reportsCyclesMissingModelsParentsAndCustomLoadersWithoutInventingCube(){
        assertEquals(MISSING_MODEL,assertThrows(BlockModelException.class,()->parser(Map.of()).parse(ResourceLocation.parse("test:missing"))).code());
        assertEquals(MISSING_PARENT,assertThrows(BlockModelException.class,()->parser(Map.of("a","{\"parent\":\"test:missing\"}")).parse(ResourceLocation.parse("test:a"))).code());
        assertEquals(PARENT_CYCLE,assertThrows(BlockModelException.class,()->parser(Map.of("a","{\"parent\":\"test:b\"}","b","{\"parent\":\"test:a\"}")).parse(ResourceLocation.parse("test:a"))).code());
        assertEquals(UNSUPPORTED_RENDERER,assertThrows(BlockModelException.class,()->parser(Map.of("a","{\"loader\":\"mod:machine\"}")).parse(ResourceLocation.parse("test:a"))).code());
    }
    @Test void missingTextureInvalidUvAndUnsupportedAnglesAreExplicit(){
        final String pattern="{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],%s\"faces\":{\"up\":{\"texture\":\"%s\"%s}}}]}";
        for(final var row:List.of(new String[]{"","#missing","",MISSING_TEXTURE.name()},new String[]{"","test:block/a",",\"uv\":[0,0,17,16]",INVALID_UV.name()},new String[]{"\"rotation\":{\"origin\":[8,8,8],\"axis\":\"y\",\"angle\":30},","test:block/a","",INVALID_GEOMETRY.name()})){
            final var error=assertThrows(BlockModelException.class,()->parser(Map.of("a",pattern.formatted(row[0],row[1],row[2]))).parse(ResourceLocation.parse("test:a")));assertEquals(row[3],error.code().name());
        }
    }
    @Test void childElementsReplaceRatherThanAccumulateParentElements(){
        final String element="{\"from\":[0,0,0],\"to\":[16,8,16],\"faces\":{\"up\":{\"texture\":\"test:block/a\"}}}";
        final var model=parser(Map.of("a","{\"elements\":["+element+","+element+"]}","b","{\"parent\":\"test:a\",\"elements\":["+element+"]}")).parse(ResourceLocation.parse("test:b"));assertEquals(1,model.elements().size());
    }
}
