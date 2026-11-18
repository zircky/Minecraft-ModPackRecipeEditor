package dev.recipeeditor.app;
import dev.recipeeditor.companionclient.RuntimeSnapshotService;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.integrations.api.IntegrationContext;
import dev.recipeeditor.integrations.gregtech.GregTechRuntimeDraft;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.ui.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.nio.file.Path;
import java.util.*;
/** Manual opt-in UI verification using a genuine previously captured runtime recipe. Test classpath only. */
public final class GregTechEditorPreview {
    public static void main(final String[] args) { Application.launch(PreviewApplication.class,args); }
    public static final class PreviewApplication extends Application {
        private final EditorTasks tasks=new EditorTasks();
        @Override public void start(final Stage owner) throws Exception {
            final var file=Path.of(getParameters().getRaw().getFirst());
            final var snapshot=new RuntimeSnapshotService().load(file);
            final var recipe=snapshot.recipes().stream().filter(r->r.type().equals("gtceu:assembler")&&r.adapter().equals("gtceu-7.1.4")&&!r.properties().has("runtimeVariants")).findFirst().orElseThrow();
            final String raw=new GregTechRuntimeDraft().copy(recipe);
            final var validator=new RecipeValidator(new ProjectRecipeParsers().parsers(),new RegistryIndex(List.of()));
            final var originals=new RecipeIndex(List.of(),new TagIndex(List.of()));
            final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of("gtceu"),file,"preview/new-copy","Explicit UI test copy of actual runtime state; not an original source");
            final var discovery=new DiscoveryResult(file.getParent(),new RegistryIndex(List.of()),originals,List.of(),0);
            owner.setTitle("Phase 7 TEST PREVIEW — actual runtime GT recipe");owner.setScene(new Scene(new Label("Manual UI preview. No user project files are written."),520,100));owner.show();
            new RecipeEditorDialog(owner,discovery,new RecipeEditorService(originals,validator),tasks,
                new RecipeEditSession(ResourceLocation.parse("mre_preview:assembler/copy"),source,raw),true,
                new IntegrationContext("1.20.1",ModLoaderType.FORGE,Map.of("gtceu","7.1.4","kubejs","2001.6.4-build.120"),Map.of())).show();
        }
        @Override public void stop(){tasks.close();}
    }
}
