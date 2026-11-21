package dev.recipeeditor.app;

import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.ui.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javafx.application.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Test-classpath-only visual probe: genuine imported ISG assets and a complete saved runtime snapshot. */
public final class ResourceBrowserPreview {
    public static void main(final String[] args) { Application.launch(Preview.class,args); }
    public static final class Preview extends Application {
        private final TextureResourceService textures=new TextureResourceService();
        private ResourceBrowserView resources;
        private MaterialsBrowserView materials;
        private final EditorTasks editingTasks=new EditorTasks();
        private final dev.recipeeditor.app.project.FileProjectPersistence persistence=new dev.recipeeditor.app.project.FileProjectPersistence();
        @Override public void start(final Stage stage) {
            stage.setTitle("Resource Browser · actual ISG verification");stage.setScene(new Scene(new Label("Importing real resources in background…"),1320,820));stage.show();
            Thread.ofVirtual().start(()->{
                try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService()) {
                    final var args=getParameters().getRaw();final var pack=importer.start(Path.of(args.get(0)),p->{}).result().get(2,TimeUnit.MINUTES).modpack();
                    final var original=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);
                    final var file=Path.of(args.get(1));final var snapshot=new RuntimeSnapshotService().load(file);final var merged=new RegistryMergeService().merge(original.registry(),snapshot,file);
                    final var result=new DiscoveryResult(original.selectedRoot(),merged.preferred(),original.recipes(),original.diagnostics(),original.elapsedMillis());
                    Platform.runLater(()->show(stage,result));
                } catch(Exception failure) { Platform.runLater(()->stage.getScene().setRoot(new TextArea(failure.toString()))); }
            });
        }
        private void show(final Stage stage,final DiscoveryResult result) {
            final var root=new BorderPane();final var recipes=new ListView<String>();
            resources=new ResourceBrowserView(result,textures,query->{recipes.getItems().setAll((query.uses()?result.recipes().findRecipesUsing(query.kind(),query.id()):result.recipes().findRecipesProducing(query.kind(),query.id())).stream().map(recipe->recipe.id()+" · "+recipe.type().displayId()).toList());root.setCenter(recipes);},
                    (entry,uses)->!(uses?result.recipes().findRecipesUsing(entry.kind(),entry.id()):result.recipes().findRecipesProducing(entry.kind(),entry.id())).isEmpty());
            materials=new MaterialsBrowserView(result,textures,form->{materials.deactivate();root.setCenter(resources);resources.showResource(form.kind(),form.resourceId());});
            final var toolbar=new ToolBar();
            for(final var kind:ResourceKind.values()) { final var button=new Button(kind.name());button.setOnAction(event->{materials.deactivate();root.setCenter(resources);resources.show(kind);});toolbar.getItems().add(button); }
            final var materialButton=new Button("Materials");materialButton.setOnAction(event->{resources.deactivate();materials.activate();root.setCenter(materials);});toolbar.getItems().add(materialButton);
            toolbar.getItems().add(new Label("Genuine runtime snapshot · "+result.registry().materials().size()+" materials"));root.setTop(toolbar);root.setCenter(resources);resources.show(ResourceKind.ITEM);
            final var validator=new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),result.registry());
            final var editor=new dev.recipeeditor.recipeengine.editor.RecipeEditorService(result.recipes(),validator);
            final var tnt=result.recipes().findById(dev.recipeeditor.core.ResourceLocation.parse("minecraft:tnt")).stream().filter(recipe->recipe.source().archiveModIds().contains("biomesoplenty")).findFirst();
            final var edit=new Button("Edit actual minecraft:tnt");edit.setDisable(tnt.isEmpty());
            edit.setOnAction(event->tnt.ifPresent(recipe->{final var current=editor.effective().findById(recipe.id()).stream().findFirst().orElse(recipe);new RecipeEditorDialog(stage,result,editor,editingTasks,new dev.recipeeditor.recipeengine.editor.RecipeEditSession(current.id(),current.source(),current.rawJson()),false,new dev.recipeeditor.integrations.api.IntegrationContext("1.20.1",dev.recipeeditor.core.importing.ModLoaderType.FORGE,Map.of(),Map.of())).showAndWait();}));
            final var status=new Label("Cached snapshot, not a live Minecraft connection");
            final var project=Path.of("build/resource-probe-project.json").toAbsolutePath();
            final var save=new Button("Save test project");save.setOnAction(event->{final var changes=editor.persistentSnapshot();persistence.save(project,dev.recipeeditor.recipeengine.editor.ProjectDocument.capture(result.selectedRoot(),changes)).whenComplete((ignored,error)->Platform.runLater(()->{if(error==null){editor.markPersisted(changes);status.setText("Test project saved: "+changes.changes().size()+" changes");}else status.setText("Save failed: "+error.getMessage());}));});
            final var reopen=new Button("Reopen test project");reopen.setOnAction(event->persistence.load(project).whenComplete((document,error)->Platform.runLater(()->{if(error!=null){status.setText("Reopen failed: "+error.getMessage());return;}editingTasks.submit(()->document.materialize(validator,project),changes->{editor.restore(changes);status.setText("Test project reopened: "+changes.changes().size()+" changes");},failure->status.setText(failure.getMessage()));})));
            root.setBottom(new ToolBar(edit,save,reopen,status));
            final var scene=new Scene(root,1320,820);scene.getStylesheets().add(Objects.requireNonNull(ResourceBrowserView.class.getResource("theme.css")).toExternalForm());stage.setScene(scene);
        }
        @Override public void stop() { if(resources!=null)resources.deactivate();if(materials!=null)materials.deactivate();textures.close();editingTasks.close();persistence.close(); }
    }
}
