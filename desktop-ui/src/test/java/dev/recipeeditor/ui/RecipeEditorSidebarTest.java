package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.RecipeMergeService;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.RecipeSourceResolver;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class RecipeEditorSidebarTest {
    @Test void selectingAnOfflineDefinitionOpensSplitSidebarAndItCanBeClosedAndReopened() throws Exception {
        FxLayoutTestSupport.run(()->{
            final var first=RecipeLayoutTestFixtures.cooking();
            final var second=RecipeLayoutTestFixtures.cooking();
            final var ids=List.of(ResourceLocation.parse("test:first"),ResourceLocation.parse("test:second"));
            final var resolver=new RecipeSourceResolver();
            final var view=new RuntimeRecipeBrowserView(List.of(
                    new RecipeMergeService.Entry(ids.getFirst(),List.of(first),null,List.of(),resolver.discover(ids.getFirst(),List.of(first.source()))),
                    new RecipeMergeService.Entry(ids.getLast(),List.of(second),null,List.of(),resolver.discover(ids.getLast(),List.of(second.source())))));
            new Scene(view,900,650);view.applyCss();view.layout();
            final var table=(TableView<?>)view.lookup("#runtime-recipe-table");
            assertNull(view.lookup("#runtime-recipe-sidebar"));
            table.getSelectionModel().select(0);
            view.applyCss(); view.layout();
            assertNotNull(view.lookup("#runtime-recipe-sidebar"));
            table.getSelectionModel().select(1);
            view.applyCss(); view.layout();
            assertTrue(view.lookupAll(".label").stream().anyMatch(node->node instanceof Label label&&label.getText().startsWith("test:second")));
            view.hideSidebar();
            view.applyCss(); view.layout();
            assertNull(view.lookup("#runtime-recipe-sidebar"));
            table.getSelectionModel().select(0);
            view.applyCss(); view.layout();
            assertNotNull(view.lookup("#runtime-recipe-sidebar"));
            view.close();
        });
    }
}
