package dev.recipeeditor.ui;

import com.google.gson.JsonParser;
import dev.recipeeditor.companionclient.RecipeMergeService;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.integrations.api.IntegrationContext;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class RecipeEditorSidebarTest {
    @Test void offlineSourcesShareTableFiltersTabsAndInlineEditor() throws Exception {
        final var fixture=new Fixture();
        try(final var tasks=new EditorTasks()) {
            FxLayoutTestSupport.run(()->{
                try(final var view=new RecipeBrowserView(fixture.discovery,fixture.editor,tasks,(recipe,format)->{},fixture.integration)) {
                    new Scene(view,1150,750);
                    view.runtime(fixture.catalog,fixture.discovery,List.of());
                    view.applyCss();view.layout();
                    final var table=(TableView<?>)view.lookup("#recipe-table");
                    assertNotNull(table);
                    assertEquals(1,table.getItems().size(),"A complete snapshot must not resurrect an absent static recipe");
                    final var search=(TextField)view.lookup("#recipe-search");
                    assertEquals(1,view.lookupAll(".text-field").stream().filter(node->node instanceof TextField field
                            && field.getPromptText()!=null && field.getPromptText().startsWith("Search")).count());
                    search.setText("minecraft:iron_ore");
                    assertEquals(1,table.getItems().size());
                    view.showRuntime();
                    view.applyCss();view.layout();
                    assertEquals("minecraft:iron_ore",search.getText());
                    assertSame(table,view.lookup("#recipe-table"));
                    assertNotNull(view.lookup(".recipe-layout"));
                    final var tabs=(TabPane)view.lookup("#recipe-details");
                    assertEquals(List.of("Recipe","Raw JSON","Source","Diagnostics"),tabs.getTabs().stream().map(Tab::getText).toList());
                    assertEquals("Recipe",tabs.getSelectionModel().getSelectedItem().getText());
                    final var toolbar=(ToolBar)view.lookup(".tool-bar");
                    final var create=(Button)toolbar.getItems().getFirst();
                    final var edit=(Button)toolbar.getItems().get(1);
                    assertFalse(create.isDisabled());
                    assertFalse(edit.isDisabled());
                    edit.fire();
                    view.applyCss();view.layout();
                    assertNotNull(view.lookup("#recipe-raw-json"));
                    assertSame(table,view.lookup("#recipe-table"));
                }
            });
        }
    }

    @Test void editsAndReopensBoundRuntimeBaseWithoutChangingTheSnapshot() {
        final var fixture=new Fixture();
        final var original=fixture.catalog.visible().recipes().getFirst();
        final var raw=original.rawJson().replace("minecraft:iron_ingot","minecraft:gold_ingot");
        final var edited=fixture.validator.validate(original.id(),raw,RecipeLayoutTestFixtures.cooking().source()).recipe();
        final var previous=fixture.observed.properties();
        fixture.editor.update(original.id(),edited);
        assertEquals(RecipeModificationStatus.MODIFIED,fixture.editor.status(original.id()));
        final var saved=fixture.editor.snapshot();
        fixture.editor.delete(original.id());
        assertTrue(fixture.editor.effective().findById(original.id()).isEmpty());
        fixture.editor.undo();
        assertEquals(edited,fixture.editor.effective().findById(original.id()).getFirst());
        final var reopened=new RecipeEditorService(fixture.discovery.recipes(),fixture.validator);
        reopened.restore(saved);
        assertTrue(reopened.hasConflict(original.id()));
        fixture.bind(reopened);
        assertFalse(reopened.hasConflict(original.id()));
        assertEquals(saved,reopened.snapshot());
        assertEquals(previous,fixture.observed.properties());
    }

    private final class Fixture {
        final RegistryIndex registry=new RegistryIndex(List.of());
        final TagIndex tags=new TagIndex(List.of());
        final RecipeValidator validator=new RecipeValidator(new Vanilla1201Parsers().parsers(),registry);
        final IntegrationContext integration=new IntegrationContext("1.20.1",ModLoaderType.FORGE,Map.of("kubejs","2001.6.4-build.120"),Map.of());
        final RuntimeData.Recipe observed=new RuntimeData.Recipe("test:observed","minecraft:blasting","minecraft:blasting",
                "BlastingRecipe","vanilla-1.20.1",RuntimeData.Support.SUPPORTED,
                JsonParser.parseString("""
                    {"ingredients":[{"item":"minecraft:iron_ore"}],"result":{"item":"minecraft:iron_ingot","count":1,"empty":false},
                     "cookingTime":100,"experience":0.7,"category":"misc"}
                    """).getAsJsonObject(),"UNKNOWN",List.of());
        final RuntimeData.Snapshot snapshot=new RuntimeData.Snapshot(2,"test",true,
                new RuntimeData.Environment("test",Path.of(".").toAbsolutePath().normalize().toString(),"1.20.1","FORGE","47","0.1",1,"2026-11-26"),
                List.of(),List.of(),List.of(observed),List.of(),List.of());
        final DiscoveryResult discovery=new DiscoveryResult(Path.of("."),registry,
                new RecipeIndex(List.of(RecipeLayoutTestFixtures.cooking()),tags),List.of(),0);
        final RuntimeRecipeCatalog.Catalog catalog=new RuntimeRecipeCatalog().prepare(
                new RecipeMergeService().merge(discovery.recipes(),snapshot),snapshot,tags,validator,integration);
        final RecipeEditorService editor=new RecipeEditorService(discovery.recipes(),validator);

        Fixture(){bind(editor);}

        void bind(final RecipeEditorService target) {
            target.applyRuntimeCatalog(registry,catalog.visible(),catalog.authorable(),catalog.incompatible(),catalog.evidence().keySet(),"test");
        }
    }
}
