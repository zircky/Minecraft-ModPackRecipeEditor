package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.*;
import java.util.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class UnifiedResourceBrowserTest {
    @Test void filtersKeepUnknownBlocksAccessibleAndCompactModeKeepsSelection()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var item=entry(ResourceKind.ITEM,"test:stone",Map.of("blockItem","true"));final var block=entry(ResourceKind.BLOCK,"test:stone",Map.of());
            final var noItem=entry(ResourceKind.BLOCK,"test:water",Map.of("hasBlockItem","false"));
            final var base=FxLayoutTestSupport.discovery();final var result=new DiscoveryResult(base.selectedRoot(),new RegistryIndex(List.of(item,block,noItem)),base.recipes(),List.of(),0);
            try(final var textures=new TextureResourceService()){
                final var view=new ResourceBrowserView(result,textures,q->{},(e,u)->false);new UiTheme().apply(new Scene(view,1280,800));view.show(ResourceKind.ITEM);refresh(view);
                assertEquals(2,view.lookupAll(".resource-card").size());
                final ComboBox<UnifiedResourceIndex.Type> type=combo(view,"Resource type");type.setValue(UnifiedResourceIndex.Type.BLOCK_ITEMS);refresh(view);assertEquals(1,view.lookupAll(".resource-card").size());
                ((Button)view.lookup(".resource-card")).fire();final ComboBox<ResourceBrowserView.Layout> layout=combo(view,"Resource layout");layout.setValue(ResourceBrowserView.Layout.COMPACT_GRID);refresh(view);
                assertTrue(view.lookup(".resource-card").getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("selected")));
                assertTrue(((TextArea)view.lookup(".text-area")).getText().contains("Block ID: UNKNOWN"));
                type.setValue(UnifiedResourceIndex.Type.BLOCKS_WITHOUT_ITEM);refresh(view);assertEquals(1,view.lookupAll(".resource-card").size());assertEquals(noItem.id(),view.lookup(".resource-card").getUserData());
                type.setValue(UnifiedResourceIndex.Type.ALL_BLOCKS);refresh(view);assertEquals(2,view.lookupAll(".resource-card").size());
                view.showResource(ResourceKind.BLOCK,block.id());refresh(view);assertTrue(((TextArea)view.lookup(".text-area")).getText().contains("Item IDs: UNKNOWN"));view.deactivate();
            }
        });
    }
    private RegistryEntry entry(final ResourceKind kind,final String id,final Map<String,String> metadata){return new RegistryEntry(ResourceLocation.parse(id),kind,id,"",Map.of(),List.of(),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"fixture",ResourceVisual.unavailable(ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED,"Unknown"),metadata);}
    private void refresh(final ResourceBrowserView view){view.applyCss();view.layout();view.applyCss();view.layout();}
    @SuppressWarnings("unchecked") private <T> ComboBox<T> combo(final ResourceBrowserView view,final String name){return (ComboBox<T>)view.lookupAll(".combo-box").stream().filter(node->name.equals(node.getAccessibleText())).findFirst().orElseThrow();}
}
