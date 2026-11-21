package dev.recipeeditor.app;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.exporters.*;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Generates reviewable exports outside the instance. Installation/reload is a separate manual step. */
@Tag("external")
final class KubeJsRuntimeExportPreparationTest {
    @Test @EnabledIfSystemProperty(named="kubeJsExportDirectory",matches=".+")
    void prepareThreeRealInstanceExportsWithoutWritingIntoTheInstance()throws Exception{
        final var directory=Files.createDirectories(Path.of(System.getProperty("kubeJsExportDirectory")));
        try(final var importer=new DefaultModpackImportService();final var analysis=new DefaultStaticAnalysisService()){
            final var pack=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES).modpack();
            final var discovered=analysis.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);
            final var validator=new RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),discovered.registry());
            final var planner=new ExportPlanner(List.of(new DatapackRecipeExporter(),new KubeJsRecipeExporter(),new CraftTweakerRecipeExporter()),new ExportValidator(validator));
            final var newId=ResourceLocation.parse("mre_export_test:vanilla_lifecycle");assertTrue(discovered.recipes().findById(newId).isEmpty());
            final String raw="{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:dirt\"}],\"result\":{\"item\":\"minecraft:cobblestone\",\"count\":2}}";
            final var created=validator.validate(newId,raw,new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),directory.resolve("project.json"),"changes/"+newId,"Explicit runtime test project overlay")).recipe();
            final var planks=ResourceLocation.parse("biomesoplenty:jacaranda_planks");final var originals=discovered.recipes().findById(planks);assertEquals(1,originals.size());
            final var base=originals.getFirst();final var updated=validator.validate(planks,new RecipeFormService().result(base.rawJson(),ResourceLocation.parse("biomesoplenty:jacaranda_planks"),5),base.source()).recipe();
            final var tnt=ResourceLocation.parse("minecraft:tnt");assertFalse(discovered.recipes().findById(tnt).isEmpty());
            final var changes=List.of(new RecipeChange(newId,RecipeChangeType.CREATE,List.of(),List.of(created),Instant.EPOCH,Map.of()),
                    new RecipeChange(planks,RecipeChangeType.UPDATE,originals.stream().map(OriginalRecipeReference::from).toList(),List.of(updated),Instant.EPOCH,Map.of()),
                    new RecipeChange(tnt,RecipeChangeType.DELETE,discovered.recipes().findById(tnt).stream().map(OriginalRecipeReference::from).toList(),List.of(),Instant.EPOCH,Map.of()));
            for(int index=0;index<changes.size();index++){
                final String stage=List.of("create","update","delete").get(index);final var target=Files.createDirectories(directory.resolve(stage));final var change=changes.get(index);
                final var mode=index==1?KubeJsExportMode.DATA_JSON:KubeJsExportMode.JAVASCRIPT;
                final var profile=new ExportProfile("mrekubejs20261118","Runtime "+stage,target,ExportFormat.KUBEJS,OverwritePolicy.REFUSE,true,"1.20.1","FORGE",mode);
                final var context=new ExportContext(pack,discovered.recipes(),new RecipeChangeSet(Map.of(change.id(),change)),directory.resolve(stage+"-project.json"),profile,List.of(),Set.of(),false);
                try(final var service=new RecipeExportService(planner)){
                    final var plan=service.dryRun(new ExportRequest(context,Set.of(change.id()),Map.of(),List.of())).get();assertTrue(plan.valid(),plan.diagnostics().toString());
                    assertEquals(ExportResult.Status.SUCCESS,service.export(plan,p->{}).result().get(20,TimeUnit.SECONDS).status());
                    Files.writeString(directory.resolve(stage+"-plan.txt"),plan.recipes().getFirst().exportMode()+"\n"+plan.files().keySet()+"\n"+plan.recipes().getFirst().provenance().describe());
                }
            }
        }
    }
}
