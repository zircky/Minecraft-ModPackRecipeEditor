package dev.recipeeditor.ui;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import javafx.scene.control.*;
import java.util.*;
@Tag("integration") final class RecipeBrowserIntegrationTest {
    @Test void sameResultRemainsTwoRecipesAndSearchUsesMachineAndTechnicalId()throws Exception {
        final var first=RecipeLayoutTestFixtures.cooking();
        final var validator=new RecipeValidator(new dev.recipeeditor.integrations.Vanilla1201Parsers().parsers(),new RegistryIndex(List.of()));
        final var second=validator.validate("other:second",first.rawJson(),first.source()).recipe();
        final var index=new RecipeIndex(List.of(first,second),new TagIndex(List.of()));
        final var discovery=new DiscoveryResult(java.nio.file.Path.of("."),new RegistryIndex(List.of()),index,List.of(),0);
        try(final var tasks=new EditorTasks()) {FxLayoutTestSupport.run(()->{
            try(final var view=new RecipeBrowserView(discovery,new RecipeEditorService(index,validator),tasks,(r,f)->{},new dev.recipeeditor.integrations.api.IntegrationContext("1.20.1",dev.recipeeditor.core.importing.ModLoaderType.FORGE,Map.of(),Map.of()))) {
                new javafx.scene.Scene(view,1200,750);view.applyCss();view.layout();
                final var table=(TableView<?>)view.lookup("#recipe-table");final var search=(TextField)view.lookup("#recipe-search");
                assertEquals(2,table.getItems().size());search.setText("FURNACE");assertEquals(2,table.getItems().size());search.setText("other:SECOND");assertEquals(1,table.getItems().size());
                table.getSelectionModel().selectFirst();assertNotNull(view.lookup(".recipe-layout"));
                assertEquals(first.rawJson(),second.rawJson());
            }
        });}
    }
}
