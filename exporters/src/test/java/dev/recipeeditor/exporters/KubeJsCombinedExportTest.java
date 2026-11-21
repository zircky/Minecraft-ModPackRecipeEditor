package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class KubeJsCombinedExportTest {
    @TempDir Path root;
    @Test void autoAndCombinedHaveOneMechanismPerIdAndNoJsAddForDataIds()throws Exception{
        final var f=new ExportFixtures(root);final var helper=new KubeJsTestSupport();final var old=f.original("minecraft:old",f.raw);final var changes=List.of(f.create("minecraft:tools/new_recipe",f.raw),f.change(old,RecipeChangeType.DELETE,""));
        for(final var mode:List.of(KubeJsExportMode.AUTO,KubeJsExportMode.COMBINED)){
            final var c=helper.context(f,changes,List.of(old),mode);final var plan=f.planner.plan(f.request(c));assertTrue(plan.valid(),plan.diagnostics().toString());assertEquals(2,plan.files().size());
            assertTrue(plan.files().containsKey("kubejs/data/minecraft/recipes/tools/new_recipe.json"));final String js=plan.files().get("kubejs/server_scripts/recipe_editor_testprofile_generated.js");assertTrue(js.contains("minecraft:old"));assertFalse(js.contains("minecraft:tools/new_recipe"));assertFalse(js.contains("event.custom"));
            assertEquals(Set.of("KUBEJS_DATA","KUBEJS_JAVASCRIPT"),plan.recipes().stream().map(PlannedRecipe::exportMode).collect(java.util.stream.Collectors.toSet()));
            assertThrows(IllegalArgumentException.class,()->new KubeJsRecipeExporter().generate(List.of(changes.getFirst(),changes.getFirst()),c));
        }
    }
    @Test void repeatExportUsesManifestRejectsUserEditsAndSwitchesModesWithoutDuplicateArtifacts()throws Exception{
        final var f=new ExportFixtures(root);final var helper=new KubeJsTestSupport();final var change=f.create("minecraft:tools/new_recipe",f.raw);final var c=helper.context(f,List.of(change),List.of(),KubeJsExportMode.DATA_JSON);
        final Path user=f.target.resolve("kubejs/server_scripts/user.js");Files.createDirectories(user.getParent());Files.writeString(user,"// user content");
        try(final var service=new RecipeExportService(f.planner)){
            final var first=service.dryRun(f.request(c)).get();assertEquals(ExportResult.Status.SUCCESS,service.export(first,p->{}).result().get(10,TimeUnit.SECONDS).status());
            final var manifests=service.workspace(f.project).get().manifests();final var again=helper.mode(c,KubeJsExportMode.DATA_JSON,manifests);final var repeated=service.dryRun(f.request(again)).get();assertTrue(repeated.valid());assertEquals(ExportResult.Status.SUCCESS,service.export(repeated,p->{}).result().get().status());
            final var switched=helper.mode(c,KubeJsExportMode.JAVASCRIPT,manifests);final var scriptPlan=service.dryRun(f.request(switched)).get();assertEquals(Set.of("kubejs/data/minecraft/recipes/tools/new_recipe.json"),scriptPlan.obsoleteFiles());assertEquals(ExportResult.Status.SUCCESS,service.export(scriptPlan,p->{}).result().get().status());
            assertFalse(Files.exists(f.target.resolve("kubejs/data/minecraft/recipes/tools/new_recipe.json")));assertEquals("// user content",Files.readString(user));
            final String path=scriptPlan.files().keySet().iterator().next();Files.writeString(f.target.resolve(path),"// user edited generated file");final var current=helper.mode(c,KubeJsExportMode.JAVASCRIPT,service.workspace(f.project).get().manifests());assertFalse(service.dryRun(f.request(current)).get().valid());assertEquals("// user edited generated file",Files.readString(f.target.resolve(path)));
        }
    }
}
