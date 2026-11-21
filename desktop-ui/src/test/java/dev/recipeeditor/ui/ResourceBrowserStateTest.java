package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.*;
import java.util.*;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class ResourceBrowserStateTest {
    @Test void languageChangeKeepsSourceFilterAndSelectedResourceAndSearchResetsPagination()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var entries=new ArrayList<RegistryEntry>();
            for(int i=0;i<121;i++)entries.add(new RegistryEntry(ResourceLocation.parse("test:item_"+i),ResourceKind.ITEM,"English "+i,"item.test.item_"+i,
                    Map.of("en_us","English "+i,"ru_ru","Translated "+i),List.of(),List.of(),DiscoveryConfidence.INFERRED_FROM_RESOURCES,"test fixture"));
            final var base=FxLayoutTestSupport.discovery();final var discovery=new DiscoveryResult(base.selectedRoot(),new RegistryIndex(entries),base.recipes(),List.of(),0);
            try(final var textures=new TextureResourceService()) {
                final var view=new ResourceBrowserView(discovery,textures,q->{},(entry,uses)->false);
                new UiTheme().apply(new Scene(view,1280,720));view.show(ResourceKind.ITEM);view.applyCss();view.layout();view.applyCss();view.layout();
                assertNull(((ListView<?>)view.lookup(".resource-grid")).getSelectionModel());
                final var card=(ResourceCardView)view.lookupAll(".resource-card").iterator().next();card.fire();
                final var id=card.getUserData();
                final var language=view.lookupAll(".combo-box").stream().filter(n->"Resource display language".equals(n.getAccessibleText())).map(n->(ComboBox<String>)n).findFirst().orElseThrow();
                final var source=view.lookupAll(".combo-box").stream().filter(n->"Resource source filter".equals(n.getAccessibleText())).map(n->(ComboBox<String>)n).findFirst().orElseThrow();
                source.getSelectionModel().select("test");view.applyCss();view.layout();
                view.lookupAll(".resource-card").stream().filter(n->id.equals(n.getUserData())).map(n->(Button)n).findFirst().orElseThrow().fire();
                language.getSelectionModel().select("ru_ru");view.applyCss();view.layout();view.applyCss();view.layout();
                assertEquals("test",source.getValue());
                assertEquals(1,view.lookupAll(".resource-card").stream().filter(n->n.getPseudoClassStates().contains(PseudoClass.getPseudoClass("selected"))).count());
                assertTrue(view.lookupAll(".resource-card").stream().filter(n->id.equals(n.getUserData())).anyMatch(n->n.getAccessibleText().startsWith("Translated")));
                final var pages=(PaginationView)view.lookup(".pagination-view");pages.setCurrentPageIndex(2);
                final var search=(TextField)view.lookupAll(".text-field").stream().filter(n->"Search resources".equals(n.getAccessibleText())).findFirst().orElseThrow();
                search.setText("missing");assertEquals(0,pages.getPageCount());assertEquals(0,pages.getCurrentPageIndex());
                search.clear();assertEquals(3,pages.getPageCount());assertEquals(0,pages.getCurrentPageIndex());view.deactivate();
            }
        });
    }
}
