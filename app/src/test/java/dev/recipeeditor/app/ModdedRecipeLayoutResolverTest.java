package dev.recipeeditor.app;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.ui.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external") @EnabledIfSystemProperty(named="realModpack",matches=".+")
final class ModdedRecipeLayoutResolverTest {
    @Test void genuineBotaniaLayoutsKeepActualSlotsAndBrewHasNoFakeItemResult()throws Exception {
        try(final var artifact=new BotaniaTestArtifact()) {
            final var recipes=List.of(artifact.recipe("data/botania/recipes/brew/allure.json"),artifact.recipe("data/botania/recipes/petal_apothecary/agricarnation.json"),artifact.recipe("data/botania/recipes/mana_infusion/acacia_leaves_dupe.json"));
            startup();fx(()->{try(final var textures=new TextureResourceService()) {
                final var discovery=new DiscoveryResult(java.nio.file.Path.of(System.getProperty("realModpack")),new RegistryIndex(List.of()),new RecipeIndex(recipes,new TagIndex(List.of())),List.of(),0);
                for(final var recipe:recipes)for(final int size:List.of(40,64,80)) {
                    final var model=new RecipeLayoutResolver().resolve(recipe).orElseThrow();assertTrue(model.machine().isPresent());
                    final var view=new RecipeLayoutRenderer(model,discovery,textures,size,"en_us",RecipeLayoutRenderer.Mode.VIEWER,null);new Scene(view,850,700);view.applyCss();view.layout();
                    final int actual=model.machine().get().groups().stream().mapToInt(group->group.slots().size()).sum();assertEquals(actual,view.lookupAll(".minecraft-recipe-slot").size());assertEquals(1,view.lookupAll(".recipe-arrow").size());
                    if(recipe.type().displayId().equals("botania:brew")){assertEquals(4,actual);assertTrue(view.lookupAll(".recipe-output-slot").isEmpty());assertTrue(view.lookupAll(".label").stream().anyMatch(node->node instanceof Label label&&label.getText().contains("Marine Allure")));}
                    assertThrows(IllegalArgumentException.class,()->new RecipeLayoutRenderer(model,discovery,textures,size,"en_us",RecipeLayoutRenderer.Mode.EDITOR,new RecipeLayoutRenderer.Actions((role,index)->{},(role,index)->{},()->{})));
                }
            }return null;});
        }
    }
    @Test void directEditorEntryCannotEnableUnverifiedBotaniaAuthoring()throws Exception {
        try(final var artifact=new BotaniaTestArtifact();final var tasks=new EditorTasks()) {
            final var recipe=artifact.recipe("data/botania/recipes/brew/allure.json");final var index=new RecipeIndex(List.of(recipe),new TagIndex(List.of()));final var discovery=new DiscoveryResult(java.nio.file.Path.of(System.getProperty("realModpack")),new RegistryIndex(List.of()),index,List.of(),0);
            final var editor=new dev.recipeeditor.recipeengine.editor.RecipeEditorService(index,artifact.validator);startup();
            final var dialog=fx(()->{final var value=new RecipeEditorDialog(null,discovery,editor,tasks,new dev.recipeeditor.recipeengine.editor.RecipeEditSession(recipe.id(),recipe.source(),recipe.rawJson()),false,new dev.recipeeditor.integrations.api.IntegrationContext("1.20.1",dev.recipeeditor.core.importing.ModLoaderType.FORGE,Map.of("botania","1.20.1-456-FORGE"),Map.of()));value.show();return value;});
            try {
                final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<deadline&&fx(()->((TextArea)dialog.getDialogPane().lookup("#recipe-raw-json")).isEditable()))Thread.sleep(30);
                fx(()->{assertFalse(((TextArea)dialog.getDialogPane().lookup("#recipe-raw-json")).isEditable());final var save=dialog.getDialogPane().lookupAll(".button").stream().filter(node->node instanceof Button button&&button.getText().equals("Save to project")).map(node->(Button)node).findFirst().orElseThrow();assertTrue(save.isDisabled());save.fire();assertTrue(editor.snapshot().changes().isEmpty());return null;});
            }finally{fx(()->{dialog.hide();return null;});}
        }
    }
    private void startup(){try{Platform.startup(()->Platform.setImplicitExit(false));}catch(IllegalStateException alreadyStarted){ }}
    private <T>T fx(final Callable<T> action)throws Exception{final var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
}
