package dev.recipeeditor.ui;

import dev.recipeeditor.ui.rendering.*;
import dev.recipeeditor.registry.ResourceKind;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class IngredientPickerPreviewTest {
    @Test void pickerUsesTheSameInventoryProviderAndDoesNotInventItsOwnTextureResolver()throws Exception{
        final var fake=new InventorySurfaceTestSupport.Client();InventoryPreviewProvider.shared().client(fake);final var picker=new AtomicReference<IngredientPicker>();
        try{FxLayoutTestSupport.run(()->{javafx.application.Platform.setImplicitExit(false);picker.set(new IngredientPicker(null,InventorySurfaceTestSupport.discovery(),ResourceKind.ITEM,false,false));picker.get().show();});
            assertEquals("MINECRAFT_CLIENT_INVENTORY",InventorySurfaceTestSupport.await(picker.get().getDialogPane()).source());assertEquals("test:inventory_icon",fake.requests.getFirst().resourceId());
        }finally{FxLayoutTestSupport.run(()->{if(picker.get()!=null)picker.get().hide();});InventoryPreviewProvider.shared().client(null);}
    }
}
