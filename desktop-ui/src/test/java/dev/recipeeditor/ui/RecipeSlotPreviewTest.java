package dev.recipeeditor.ui;

import dev.recipeeditor.ui.rendering.*;
import dev.recipeeditor.registry.TextureResourceService;
import java.util.concurrent.atomic.*;
import javafx.scene.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class RecipeSlotPreviewTest {
    @Test void inputAndOutputSlotsShareDefaultIconWhileNbtVariantHasItsOwnRequestAndImage()throws Exception{
        final var fake=new InventorySurfaceTestSupport.Client();InventoryPreviewProvider.shared().client(fake);final var stage=new AtomicReference<Stage>();final var slots=new AtomicReference<HBox>();final var variant=new AtomicReference<RecipeSlotView>();
        try(final var textures=new TextureResourceService()){
            FxLayoutTestSupport.run(()->{javafx.application.Platform.setImplicitExit(false);final var discovery=InventorySurfaceTestSupport.discovery();final var content=RecipeSlotContent.parse("{\"item\":\"test:inventory_icon\"}");final var a=new RecipeSlotView(content,discovery,textures);final var b=new RecipeSlotView(content,discovery,textures);variant.set(new RecipeSlotView(RecipeSlotContent.parse("{\"item\":\"test:inventory_icon\",\"nbt\":\"{color:1}\"}"),discovery,textures));slots.set(new HBox(a,b,variant.get()));final var window=new Stage();stage.set(window);window.setScene(new Scene(slots.get(),300,100));window.show();});
            assertEquals("MINECRAFT_CLIENT_INVENTORY",InventorySurfaceTestSupport.await(variant.get()).source());InventorySurfaceTestSupport.await(slots.get());assertEquals(2,fake.requests.size(),"Default slots must coalesce; NBT must remain distinct");assertTrue(fake.requests.stream().anyMatch(r->r.variant().equals("nbt")&&r.nbt().equals("{color:1}")));
        }finally{FxLayoutTestSupport.run(()->{if(stage.get()!=null)stage.get().close();});InventoryPreviewProvider.shared().client(null);}
    }
}
