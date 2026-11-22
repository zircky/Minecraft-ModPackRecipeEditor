package dev.recipeeditor.ui;

import dev.recipeeditor.registry.rendering.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import dev.recipeeditor.ui.rendering.StaticInventoryRenderer;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class StaticInventoryRendererTest {
    @Test void guiScaleChangesActualIconFootprintAndOutputKeepsTransparentBackground()throws Exception{
        FxLayoutTestSupport.run(()->javafx.application.Platform.setImplicitExit(false));
        final var geometry=BlockRenderFixtures.cube(16,0,null);final var textures=BlockRenderFixtures.textures(geometry);final var renderer=new StaticInventoryRenderer();
        final var a=new InventoryItemModel(geometry.id(),InventoryItemModel.Kind.GEOMETRY,geometry,null,new GuiDisplayTransform(new Vector(30,225,0),new Vector(0,0,0),new Vector(.625,.625,.625),new Vector(0,0,0)),"side",List.of());
        final var b=new InventoryItemModel(geometry.id(),InventoryItemModel.Kind.GEOMETRY,geometry,null,new GuiDisplayTransform(new Vector(30,225,0),new Vector(0,0,0),new Vector(.3,.3,.3),new Vector(0,0,0)),"side",List.of());
        final var first=renderer.render(a,textures,64).get(5,TimeUnit.SECONDS);final var second=renderer.render(b,textures,64).get(5,TimeUnit.SECONDS);
        assertEquals(0,first.argb()[0]>>>24);assertTrue(area(first)>area(second)*2,"Model GUI scale must be used instead of automatic fit-to-view");assertTrue(area(second)>0);
    }
    private long area(final StaticInventoryRenderer.Pixels image){return java.util.Arrays.stream(image.argb()).filter(pixel->(pixel>>>24)>0).count();}
}
