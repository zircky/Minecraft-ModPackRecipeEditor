package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GuiDisplayTransformTest {
    @Test void inheritsGuiTransformFromRealItemParentAndClampsLikeMinecraft1201(){
        final String parent=InventoryItemModelResolverTest.element().replaceFirst("\\{","{\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[160,-160,16],\"scale\":[10,-10,2]}},");
        final var model=InventoryItemModelResolverTest.resolver(Map.of("test:item/a","{\"parent\":\"test:block/base\"}","test:block/base",parent)).resolve(ResourceLocation.parse("test:a"));
        assertEquals(new Vector(30,225,0),model.gui().rotation());assertEquals(new Vector(5,-5,1),model.gui().translation());assertEquals(new Vector(4,-4,2),model.gui().scale());
    }
    @Test void explicitlySpecifiedChildGuiUsesIdentityForOmittedComponentsRatherThanMixingParents(){
        final String parent=InventoryItemModelResolverTest.element().replaceFirst("\\{","{\"display\":{\"gui\":{\"rotation\":[30,225,0]}},");
        final var model=InventoryItemModelResolverTest.resolver(Map.of("test:item/a","{\"parent\":\"test:block/base\",\"display\":{\"gui\":{\"scale\":[0.5,0.5,0.5]}}}","test:block/base",parent)).resolve(ResourceLocation.parse("test:a"));
        assertEquals(new Vector(0,0,0),model.gui().rotation());assertEquals(new Vector(.5,.5,.5),model.gui().scale());
    }
    @Test void absentGuiTransformIsMinecraftIdentityRatherThanAnInventedIsometricCamera(){assertEquals(GuiDisplayTransform.identity(),InventoryItemModelResolverTest.resolver(Map.of("test:item/a",InventoryItemModelResolverTest.element())).resolve(ResourceLocation.parse("test:a")).gui());}
}
