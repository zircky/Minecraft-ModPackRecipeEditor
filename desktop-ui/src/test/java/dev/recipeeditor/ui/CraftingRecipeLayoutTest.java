package dev.recipeeditor.ui;

import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class CraftingRecipeLayoutTest {
    @Test void scalableNineSlotWorkbenchHasOneArrowAndSelectionRequiresExplicitEditOrClear()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var values=new ArrayList<String>();for(int i=0;i<9;i++)values.add(i%2==0?"{\"item\":\"minecraft:gunpowder\"}":"{\"item\":\"minecraft:sand\"}");
            try(final var textures=new TextureResourceService()) {
                for(final int size:List.of(40,64,80)) {
                    final var edit=new AtomicInteger(-1);final var clear=new AtomicInteger(-1);
                    final var grid=new ShapedCraftingEditorView(values,FxLayoutTestSupport.discovery(),textures,size,null,null);
                    final var workbench=new CraftingRecipeEditorView(grid,values,new Label("Output"),-1,index->{},edit::set,clear::set);
                    new UiTheme().apply(new Scene(workbench,800,500));workbench.applyCss();workbench.layout();
                    assertEquals(9,grid.getChildren().size());assertEquals(1,workbench.lookupAll(".recipe-arrow").size());
                    for(final var node:grid.getChildren()){final var slot=(RecipeSlotView)node;assertEquals(size,slot.getPrefWidth());assertEquals(size,slot.getPrefHeight());}
                    ((RecipeSlotView)grid.getChildren().get(4)).fire();assertEquals(-1,edit.get());
                    workbench.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().startsWith("Choose / Replace")).map(n->(Button)n).findFirst().orElseThrow().fire();assertEquals(4,edit.get());
                    workbench.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Clear Selected Slot")).map(n->(Button)n).findFirst().orElseThrow().fire();assertEquals(4,clear.get());
                }
            }
        });
    }
    @Test void mandatoryResultRemainsPickableThroughTheSlotWithoutInlineRemovalControls()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),java.nio.file.Path.of("project"),"changes/test:recipe","fixture");
            final var recipe=new RecipeValidator(new Vanilla1201Parsers().parsers(),new RegistryIndex(List.of())).validate("test:recipe",new RecipeFormService().template("minecraft:crafting_shapeless"),source).recipe();
            try(final var textures=new TextureResourceService()) {
                final var output=new RecipeOutputEditor(null,FxLayoutTestSupport.discovery(),recipe,textures,64,(id,count)->{});
                final var slot=(RecipeSlotView)output.getChildren().getFirst();
                assertNotNull(slot.getOnAction());assertEquals(64,slot.getPrefWidth());assertEquals(1,output.getChildren().size());
                assertNotNull(slot.getContextMenu().getItems().getFirst().getOnAction());assertTrue(slot.getContextMenu().getItems().getLast().isDisable());
                assertTrue(output.lookupAll(".recipe-arrow").isEmpty());
            }
        });
    }
    @Test void repeatedWarningsKeepTheirSeverityAndActualAffectedSlots() {
        final var issue=new ValidationIssue(ValidationIssue.Severity.WARNING,"ingredient","Item minecraft:gunpowder is not confirmed by runtime registry");
        final var grid=List.of("{\"item\":\"minecraft:gunpowder\"}","{\"item\":\"minecraft:sand\"}","{\"item\":\"minecraft:gunpowder\"}");
        final var validation=new RecipeValidationResult(null,List.of(issue,issue));
        final var preview=new RecipeEditPreview(validation,true,"minecraft:crafting_shaped",Map.of(),grid,List.of(),List.of(),"");
        final String text=new RecipeValidationPresenter().format(preview);
        assertEquals(1,text.split("WARNING",-1).length-1);assertTrue(text.contains("Affected slots: 1, 3"));assertFalse(text.contains("ERROR"));
    }
}
