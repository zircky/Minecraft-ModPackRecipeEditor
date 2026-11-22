package dev.recipeeditor.ui;

import dev.recipeeditor.ui.rendering.*;
import dev.recipeeditor.registry.*;
import java.util.concurrent.atomic.*;
import javafx.scene.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class ResourceBrowserPreviewTest {
    @Test void browserLoadsInventoryProviderForVisibleCardsRatherThanUsingFlatSpriteEvidence()throws Exception{
        final var fake=new InventorySurfaceTestSupport.Client();InventoryPreviewProvider.shared().client(fake);final var stage=new AtomicReference<Stage>();final var browser=new AtomicReference<ResourceBrowserView>();
        try{FxLayoutTestSupport.run(()->{javafx.application.Platform.setImplicitExit(false);final var view=new ResourceBrowserView(InventorySurfaceTestSupport.discovery(),new TextureResourceService(),q->{},(e,u)->false);browser.set(view);final var window=new Stage();stage.set(window);window.setScene(new Scene(view,1100,700));window.show();view.show(ResourceKind.ITEM);});
            final var image=InventorySurfaceTestSupport.await(browser.get());assertEquals("MINECRAFT_CLIENT_INVENTORY",image.source());assertTrue(image.png().length>0);assertEquals("test:inventory_icon",fake.requests.getFirst().resourceId());FxLayoutTestSupport.run(()->assertTrue(browser.get().lookupAll("*").stream().noneMatch(node->node instanceof javafx.scene.SubScene),"Cards must only contain cached images"));
        }finally{FxLayoutTestSupport.run(()->{if(stage.get()!=null)stage.get().close();if(browser.get()!=null)browser.get().deactivate();});InventoryPreviewProvider.shared().client(null);}
    }
}
