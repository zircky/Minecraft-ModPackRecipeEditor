package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the wizard's persisted selection/preview contract; does not pretend to be a rendered JavaFX test. */
final class KubeJsExportWizardTest {
    @TempDir Path root;
    @Test void selectedModesPersistAndPreviewKeepsUnsupportedRowsVisible()throws Exception{
        final var f=new ExportFixtures(root);final var helper=new KubeJsTestSupport();final var old=f.original("minecraft:tnt",f.raw);final var change=f.change(old,RecipeChangeType.DELETE,"");
        final var metadata=new ExportMetadataStore();for(final var mode:KubeJsExportMode.values()){
            final var c=helper.context(f,List.of(change),List.of(old),mode);metadata.saveProfile(f.project,c.profile());assertEquals(mode,metadata.load(f.project).profiles().getFirst().kubeJsMode());
            final var plan=f.planner.plan(f.request(c));assertEquals(1,plan.recipes().size());assertEquals(change.id(),plan.recipes().getFirst().change().id());
            if(mode==KubeJsExportMode.DATA_JSON){assertFalse(plan.valid());assertTrue(plan.recipes().getFirst().targetFiles().isEmpty());}else {assertTrue(plan.valid());assertEquals("KUBEJS_JAVASCRIPT",plan.recipes().getFirst().exportMode());assertEquals(List.copyOf(plan.files().keySet()),plan.recipes().getFirst().targetFiles());}
        }
    }
    @Test void legacyProfileConstructorRetainsJavascriptRatherThanSilentlyChangingMechanism()throws Exception{
        final var f=new ExportFixtures(root);assertEquals(KubeJsExportMode.JAVASCRIPT,f.context(List.of(f.create("test:legacy",f.raw)),ExportFormat.KUBEJS).profile().kubeJsMode());
    }
    @Test void legacyMetadataWithoutModeLoadsAsJavascript()throws Exception{
        final var f=new ExportFixtures(root);final var store=new ExportMetadataStore();store.saveProfile(f.project,f.context(List.of(f.create("test:legacy",f.raw)),ExportFormat.KUBEJS).profile());
        final var text=new ExportText();final var envelope=(com.fasterxml.jackson.databind.node.ObjectNode)text.json(java.nio.file.Files.readString(store.path(f.project)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)envelope.path("workspace").path("profiles").get(0)).remove("kubeJsMode");envelope.put("sha256",ExportText.hash(envelope.path("workspace").toString()));java.nio.file.Files.writeString(store.path(f.project),envelope.toString());
        assertEquals(KubeJsExportMode.JAVASCRIPT,store.load(f.project).profiles().getFirst().kubeJsMode());
    }
}
