package dev.recipeeditor.app;

import dev.recipeeditor.app.project.FileProjectPersistence;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.integrations.api.IntegrationContext;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.ui.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Real graphical integration: genuine imported TNT, actual controls, draft history and disk reopen. */
@Tag("external")
final class RealRecipeEditorUiTest {
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void actualTntCanBeVisuallyClearedUndoneRedoneEditedSavedAndReopened()throws Exception {
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var tasks=new EditorTasks();final var persistence=new FileProjectPersistence()) {
            final var pack=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES).modpack();
            final var discovery=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);
            final var recipe=discovery.recipes().findById(ResourceLocation.parse("minecraft:tnt")).stream().filter(r->r.source().archiveModIds().contains("biomesoplenty")).findFirst().orElseThrow();
            final var validator=new RecipeValidator(new ProjectRecipeParsers().parsers(),discovery.registry());
            final var editor=new RecipeEditorService(discovery.recipes(),validator);
            try{Platform.startup(()->{});}catch(IllegalStateException alreadyStarted){ }
            final var dialog=fx(()->{Platform.setImplicitExit(false);final var value=new RecipeEditorDialog(null,discovery,editor,tasks,new RecipeEditSession(recipe.id(),recipe.source(),recipe.rawJson()),false,IntegrationContext.from(pack));value.show();return value;});
            try {
                await(()->!button(dialog,"Save to project").isDisabled());
                final String original=fx(()->raw(dialog).getText());
                fx(()->{final var grid=(ShapedCraftingEditorView)dialog.getDialogPane().lookup("ShapedCraftingEditorView");((RecipeSlotView)grid.getChildren().get(4)).fire();button(dialog,"Clear Selected Slot").fire();return null;});
                await(()->!raw(dialog).getText().equals(original)&&!button(dialog,"Save to project").isDisabled());
                fx(()->{button(dialog,"Undo draft").fire();return null;});await(()->raw(dialog).getText().equals(original)&&!button(dialog,"Save to project").isDisabled());
                fx(()->{button(dialog,"Redo draft").fire();return null;});await(()->!raw(dialog).getText().equals(original)&&!button(dialog,"Save to project").isDisabled());
                fx(()->{button(dialog,"Undo draft").fire();return null;});await(()->raw(dialog).getText().equals(original)&&!button(dialog,"Save to project").isDisabled());
                final String changed=new RecipeFormService().result(original,ResourceLocation.parse("minecraft:tnt"),2);
                fx(()->{final var tabs=(TabPane)dialog.getDialogPane().lookup(".tab-pane");tabs.getSelectionModel().select(2);raw(dialog).setText(changed);return null;});
                await(()->!button(dialog,"Save to project").isDisabled());
                fx(()->{((TabPane)dialog.getDialogPane().lookup(".tab-pane")).getSelectionModel().select(0);return null;});
                await(()->dialog.getDialogPane().lookupAll("ImageView").stream().filter(node->((javafx.scene.image.ImageView)node).getImage()!=null).count()>=6);
                fx(()->{assertEquals(1,dialog.getDialogPane().lookupAll(".recipe-arrow").size());final var output=(RecipeSlotView)dialog.getDialogPane().lookup(".recipe-output-slot");assertEquals(64,output.getPrefWidth());return null;});
                // Exercise the real Result Picker without changing its ID or dismissing another process's UI.
                final var pickerChecked=new CompletableFuture<Boolean>();
                fx(()->{Platform.runLater(()->{try{final var picker=Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().equals("Pick ingredient")).findFirst().orElseThrow();final var pane=(DialogPane)picker.getScene().getRoot();assertFalse(pane.lookupAll(".pagination-view").isEmpty());((Button)pane.lookupButton(ButtonType.CANCEL)).fire();pickerChecked.complete(true);}catch(Throwable error){pickerChecked.completeExceptionally(error);}});((RecipeSlotView)dialog.getDialogPane().lookup(".recipe-output-slot")).fire();return null;});
                assertTrue(pickerChecked.get(15,TimeUnit.SECONDS));
                final var image=fx(()->{final var snapshot=dialog.getDialogPane().snapshot(null,null);final int w=(int)snapshot.getWidth(),h=(int)snapshot.getHeight();final var pixels=new int[w*h];snapshot.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);final var result=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,w,h,pixels,0,w);return result;});
                Files.createDirectories(Path.of("build"));javax.imageio.ImageIO.write(image,"PNG",Path.of("build/real-editor-ui.png").toFile());
                fx(()->{button(dialog,"Save to project").fire();return null;});
                await(()->editor.snapshot().changes().containsKey(recipe.id())&&!button(dialog,"Save to project").isDisabled());
                final var project=Path.of("build/real-editor-ui-project.json").toAbsolutePath();
                persistence.write(project,ProjectDocument.capture(pack.selectedDirectory(),editor.snapshot()));
                final var reopened=persistence.read(project).materialize(validator,project);
                // TextArea normalizes Windows CRLF to LF; compare persisted UI text and JSON semantics.
                assertEquals(fx(()->raw(dialog).getText()),reopened.changes().get(recipe.id()).replacement().getFirst().rawJson());
                assertEquals(new com.fasterxml.jackson.databind.ObjectMapper().readTree(changed),new com.fasterxml.jackson.databind.ObjectMapper().readTree(reopened.changes().get(recipe.id()).replacement().getFirst().rawJson()));
                assertEquals(recipe.source(),reopened.changes().get(recipe.id()).replacement().getFirst().source());
            }finally{fx(()->{dialog.hide();return null;});}
        }
    }
    private Button button(final RecipeEditorDialog dialog,final String text){return dialog.getDialogPane().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow();}
    private TextArea raw(final RecipeEditorDialog dialog){return (TextArea)dialog.getDialogPane().lookup("#recipe-raw-json");}
    private <T> T fx(final Callable<T> work)throws Exception{final var task=new FutureTask<>(work);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private void await(final Callable<Boolean> condition)throws Exception{final long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<limit){if(fx(condition))return;Thread.sleep(50);}fail("Timed out waiting for JavaFX/editor state");}
}
