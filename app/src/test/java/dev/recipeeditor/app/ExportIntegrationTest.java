package dev.recipeeditor.app;

import dev.recipeeditor.app.project.FileProjectPersistence;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.exporters.*;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("integration")
final class ExportIntegrationTest {
    @TempDir Path root;
    private final String raw="{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"minecraft:iron_ingot\",\"experience\":0.7,\"cookingtime\":200}";
    @Test void importsLoadsSavedChangesAndExportsThreeFormatsWithoutChangingSource()throws Exception {
        final var source=Files.createDirectories(root.resolve("instance"));Files.createDirectories(source.resolve("mods"));
        Files.writeString(source.resolve("manifest.json"),"{\"minecraft\":{\"version\":\"1.20.1\",\"modLoaders\":[{\"id\":\"forge-47.3.0\"}]}}");
        final var jar=source.resolve("mods/test.jar");
        try(final var zip=new ZipOutputStream(Files.newOutputStream(jar))) {
            entry(zip,"META-INF/mods.toml","""
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"
                [[mods]]
                modId="kubejs"
                version="2001.6.4-build.120"
                displayName="Test dependency metadata (no executable code)"
                [[mods]]
                modId="crafttweaker"
                version="14.0.57"
                displayName="Test dependency metadata (no executable code)"
                """);
            entry(zip,"data/test/recipes/base.json",raw);
        }
        final byte[] before=Files.readAllBytes(jar);
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var persistence=new FileProjectPersistence()) {
            final var imported=importer.start(source,p->{}).result().get(20,TimeUnit.SECONDS);
            final var discovery=analyzer.start(imported.modpack(),p->{}).result().get(20,TimeUnit.SECONDS);
            final var original=discovery.recipes().recipes().getFirst();
            final var validator=new RecipeValidator(new Vanilla1201Parsers().parsers(),discovery.registry());
            final var editor=new RecipeEditorService(discovery.recipes(),validator);
            editor.update(original.id(),validator.validate(original.id(),raw.replace("200","300"),original.source()).recipe());
            editor.duplicate(editor.effective().findById(original.id()).getFirst(),ResourceLocation.parse("crafttweaker:copy"));
            final var project=root.resolve("project.json");persistence.write(project,ProjectDocument.capture(source,editor.snapshot()));
            // A later edit in memory must never leak into export loaded from disk.
            editor.duplicate(editor.effective().findById(original.id()).getFirst(),ResourceLocation.parse("test:unsaved"));
            final var saved=persistence.read(project).materialize(validator,project);
            final var planner=new ExportPlanner(List.of(new DatapackRecipeExporter(),new KubeJsRecipeExporter(),new CraftTweakerRecipeExporter()),new ExportValidator(validator));
            try(final var exports=new RecipeExportService(planner)) {
                for(final var format:List.of(ExportFormat.DATAPACK,ExportFormat.KUBEJS,ExportFormat.CRAFTTWEAKER)) {
                    final var target=Files.createDirectories(root.resolve(format.name()));
                    final var profile=new ExportProfile(format.name().toLowerCase()+"test","Integration",target,format,OverwritePolicy.OWNED_UNCHANGED,true,"1.20.1","FORGE");
                    final var context=new ExportContext(imported.modpack(),discovery.recipes(),saved,project,profile,List.of(),Set.of(),false);
                    final var selection=format==ExportFormat.CRAFTTWEAKER?Set.of(ResourceLocation.parse("crafttweaker:copy")):saved.changes().keySet();
                    final var plan=exports.dryRun(new ExportRequest(context,selection,Map.of(),List.of())).get(10,TimeUnit.SECONDS);
                    assertTrue(plan.valid(),plan.diagnostics().toString());try(final var files=Files.list(target)){assertEquals(0,files.count());}
                    assertFalse(plan.recipes().stream().anyMatch(p->p.change().id().toString().equals("test:unsaved")));
                    final var result=exports.export(plan,p->{}).result().get(10,TimeUnit.SECONDS);assertEquals(ExportResult.Status.SUCCESS,result.status(),result.diagnostics().toString());
                    for(final var generated:plan.files().entrySet())assertEquals(generated.getValue(),Files.readString(target.resolve(generated.getKey())));
                }
                assertEquals(3,exports.workspace(project).get(10,TimeUnit.SECONDS).manifests().size());
            }
            assertArrayEquals(before,Files.readAllBytes(jar));
        }
    }
    @Test @Tag("external") @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void exportsActualStaticRecipeToTemporaryDirectoryOnly()throws Exception {
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var persistence=new FileProjectPersistence()) {
            final var imported=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES);
            final var discovery=analyzer.start(imported.modpack(),p->{}).result().get(5,TimeUnit.MINUTES);
            final var validator=new RecipeValidator(new Vanilla1201Parsers().parsers(),discovery.registry());
            final var planner=new ExportPlanner(List.of(new DatapackRecipeExporter(),new KubeJsRecipeExporter(),new CraftTweakerRecipeExporter()),new ExportValidator(validator));
            final var original=discovery.recipes().recipes().stream().filter(r->r.status()==RecipeParseStatus.PARSED&&discovery.recipes().findById(r.id()).size()==1).findFirst().orElseThrow();
            final byte[] before=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(original.source().archivePath()));
            final var editor=new RecipeEditorService(discovery.recipes(),validator);editor.duplicate(original,ResourceLocation.parse("recipeeditor_test:phase5_copy"));
            final var project=root.resolve("real-project.json");persistence.write(project,ProjectDocument.capture(imported.modpack().selectedDirectory(),editor.snapshot()));
            final var target=Files.createDirectories(root.resolve("real-export"));final var profile=new ExportProfile("realtestprofile","Real static test",target,ExportFormat.AUTO,OverwritePolicy.REFUSE,true,"1.20.1","FORGE");
            final var saved=persistence.read(project).materialize(validator,project);final var context=new ExportContext(imported.modpack(),discovery.recipes(),saved,project,profile,List.of(),Set.of(),false);
            try(final var exports=new RecipeExportService(planner)) {final var p=exports.dryRun(new ExportRequest(context,saved.changes().keySet(),Map.of(),List.of())).get(30,TimeUnit.SECONDS);assertTrue(p.valid(),p.diagnostics().toString());assertEquals(ExportResult.Status.SUCCESS,exports.export(p,x->{}).result().get(30,TimeUnit.SECONDS).status());
                Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/real-export-result.txt"),"Actual static recipe: "+original.id()+"\nSource: "+original.source().archivePath()+"\nGenerated files: "+p.files().keySet()+"\nJAR SHA-256 unchanged; source instance was read only. No Minecraft runtime test.\n");}
            assertArrayEquals(before,java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(original.source().archivePath())));
        }
    }
    private void entry(final ZipOutputStream zip,final String path,final String text)throws Exception {zip.putNextEntry(new ZipEntry(path));zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
}
