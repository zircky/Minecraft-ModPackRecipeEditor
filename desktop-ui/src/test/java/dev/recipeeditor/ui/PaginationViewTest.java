package dev.recipeeditor.ui;

import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class PaginationViewTest {
    @Test void largeCatalogueAllowsDirectFirstLastAndEmptyNavigationWithoutHundredsOfNodes()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var page=new PaginationView();final var rendered=new AtomicInteger();
            page.setPageFactory(index->{rendered.set(index);return new Label("Content "+index);});page.setPageCount(201);
            final var root=new VBox(page);new UiTheme().apply(new Scene(root,700,500));root.applyCss();root.layout();
            button(page,"Last page").fire();assertEquals(200,page.getCurrentPageIndex());assertEquals(200,rendered.get());
            assertTrue(button(page,"Next page").isDisabled());
            button(page,"First page").fire();assertEquals(0,rendered.get());
            final var jump=(TextField)page.lookup(".text-field");jump.setText("151");jump.fireEvent(new javafx.event.ActionEvent());assertEquals(150,rendered.get());
            jump.setText("202");jump.fireEvent(new javafx.event.ActionEvent());assertEquals(150,page.getCurrentPageIndex());
            assertTrue(page.lookupAll(".page-number").size()<=5);
            page.setPageCount(2);assertTrue(page.getCurrentPageIndex()<2);
            page.setCurrentPageIndex(0);assertEquals(0,rendered.get());
            page.setPageCount(0);assertTrue(page.lookupAll(".page-number").isEmpty());assertTrue(jump.isDisabled());
            assertTrue(button(page,"First page").isDisabled());assertTrue(button(page,"Last page").isDisabled());
        });
    }
    private Button button(final PaginationView page,final String name){return (Button)page.lookupAll(".button").stream().filter(node->name.equals(node.getAccessibleText())).findFirst().orElseThrow();}
}
