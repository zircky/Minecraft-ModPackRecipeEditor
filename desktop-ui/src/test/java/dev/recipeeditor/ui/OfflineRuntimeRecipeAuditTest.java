package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.exporters.*;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.integrations.api.IntegrationContext;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
@EnabledIfSystemProperty(named="runtimeSnapshotFile",matches=".+")
final class OfflineRuntimeRecipeAuditTest {
    @TempDir Path output;

    @Test void displaysAndEditsTheCapturedDeshRecipeOffline() throws Exception {
        final var database=Path.of(System.getProperty("runtimeSnapshotFile")).toAbsolutePath();
        final var store=new SqliteSnapshotStore(database.getParent().getParent());
        final var captured=store.loadLatest(new ModpackFingerprint(database.getParent().getFileName().toString())).orElseThrow();
        final var pack=Path.of(captured.environment().sourceInstance());
        final var originals=new RecipeIndex(List.of(),new TagIndex(List.of()));
        final var desh=ResourceLocation.parse("ad_astra:blasting/desh_ingot_from_blasting_deepslate_desh_ore");
        final var needed=Set.of("ad_astra:desh_ingot","ad_astra:deepslate_desh_ore","ad_astra:moon_desh_ore","minecraft:gold_ingot");
        final var resources=new RuntimeData.Snapshot(2,captured.snapshotId(),true,captured.environment(),
                captured.registries().stream().filter(row->row.kind()==RuntimeData.Kind.ITEM&&needed.contains(row.id())).toList(),
                List.of(),List.of(),List.of(),List.of());
        final var registry=new RegistryMergeService().merge(assets(pack),resources,database).preferred();
        final var validator=new RecipeValidator(new ProjectRecipeParsers().parsers(),registry);
        final var integration=new IntegrationContext("1.20.1",ModLoaderType.FORGE,
                captured.mods().stream().collect(java.util.stream.Collectors.toMap(RuntimeData.Mod::id,RuntimeData.Mod::version)),Map.of());
        final long start=System.nanoTime();
        final var catalog=new RuntimeRecipeCatalog().prepare(new RecipeMergeService().merge(originals,captured),captured,originals.tagIndex(),validator,integration);
        System.out.printf("Prepared %,d recipes offline in %.2f s; %,d verified authoring bases%n",catalog.visible().recipes().size(),
                (System.nanoTime()-start)/1e9,catalog.authorable().recipes().size());
        assertEquals(captured.recipes().size(),catalog.visible().recipes().size());
        final var shapedId=ResourceLocation.parse("gtceu:shaped/crowbar_titanium_black");
        final var shaped=new RecipeLayoutResolver().resolve(catalog.visible().findById(shapedId).getFirst()).orElseThrow();
        assertEquals(RecipeLayoutProvider.Kind.SHAPED,shaped.kind());
        assertEquals(9,shaped.inputs().size());
        assertFalse(shaped.output().nbt().isBlank());
        for(final String id:List.of("gtceu:assembler/assemble_nether_bricks_into_wall",
                "gtceu:chemical_reactor/carbon_monoxide_from_coal_gem")) {
            final var machine=ResourceLocation.parse(id);
            assertFalse(catalog.visible().findById(machine).isEmpty());
            assertTrue(catalog.authorable().findById(machine).isEmpty());
        }
        final var editor=new RecipeEditorService(originals,validator);
        editor.applyRuntimeCatalog(registry,catalog.visible(),catalog.authorable(),catalog.incompatible(),catalog.evidence().keySet(),"cached");
        final var recipe=editor.effective().findById(desh).getFirst();
        assertTrue(editor.canChange(desh));
        assertEquals("Дешовый слиток",new RecipeDisplayNameResolver(registry,new RecipeTypeDisplayNameResolver()).resolve(recipe,"ru_ru").name());
        final var layout=new RecipeLayoutResolver().resolve(recipe).orElseThrow();
        assertEquals("100",layout.parameters().get("Cooking time (ticks)"));
        assertEquals("0.1",layout.parameters().get("Experience"));
        verifyWindow(editor,registry,catalog,pack,integration,desh);
        final var forms=new RecipeFormService();
        final var withInput=forms.patch(recipe.rawJson(),"ingredient","{\"item\":\"ad_astra:moon_desh_ore\"}");
        final var raw=forms.result(withInput,ResourceLocation.parse("minecraft:gold_ingot"),1);
        editor.update(desh,validator.validate(desh,raw,new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),Path.of("test-project.json"),
                "changes/"+desh,"offline test")).recipe());
        assertEquals(RecipeModificationStatus.MODIFIED,editor.status(desh));
        assertTrue(editor.snapshot().changes().get(desh).replacement().getFirst().rawJson().contains("minecraft:gold_ingot"));
        assertTrue(catalog.evidence().get(desh).runtime().properties().getAsJsonObject("result").get("item").getAsString().equals("ad_astra:desh_ingot"));
        final var modpack=new ModpackDefinition(pack,pack,"ISG",new MinecraftVersion("1.20.1",List.of("snapshot")),
                new ModLoaderDefinition(ModLoaderType.FORGE,captured.environment().loaderVersion(),List.of("snapshot")),List.of());
        final var context=new ExportContext(modpack,editor.authoringBases(),editor.snapshot(),output.resolve("project.json"),
                new ExportProfile("isgdeshaudit","ISG",output,ExportFormat.DATAPACK,OverwritePolicy.REFUSE,true,"1.20.1","FORGE"),
                List.of(),Set.of(),false);
        final var plan=new ExportPlanner(List.of(new DatapackRecipeExporter()),new ExportValidator(validator))
                .plan(new ExportRequest(context,Set.of(desh),Map.of(),List.of()));
        assertTrue(plan.valid(),plan.diagnostics().toString());
        assertTrue(plan.files().values().stream().anyMatch(file->file.contains("ad_astra:moon_desh_ore")&&file.contains("minecraft:gold_ingot")));
    }

    private RegistryIndex assets(final Path pack) throws Exception {
        try(var files=Files.list(pack.resolve("mods"))) {
            final var jar=files.filter(file->file.getFileName().toString().startsWith("ad_astra-forge-")).findFirst().orElseThrow();
            try(var zip=new ZipFile(jar.toFile())) {
                final var documents=new ArrayList<ResourceDocument>();
                final var textures=new ArrayList<ResourceSource>();
                for(final String entry:List.of("models/item/desh_ingot.json","textures/item/desh_ingot.png","lang/en_us.json","lang/ru_ru.json")) {
                    final var path="assets/ad_astra/"+entry;
                    final var source=new ResourceSource(SourceType.MOD_JAR,List.of("ad_astra"),jar,path,"ISG copy");
                    if(entry.endsWith(".png"))textures.add(source);
                    else if(zip.getEntry(path)!=null) {
                        try(var input=zip.getInputStream(zip.getEntry(path))) {
                            documents.add(new ResourceDocument(source,new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
                        }
                    }
                }
                return new RegistryIndex(List.of(),new ResourceScanResult(documents,textures,List.of()));
            }
        }
    }

    private void verifyWindow(final RecipeEditorService editor,final RegistryIndex registry,final RuntimeRecipeCatalog.Catalog catalog,
            final Path pack,final IntegrationContext integration,final ResourceLocation desh) throws Exception {
        final var discovery=new DiscoveryResult(pack,registry,editor.originals(),List.of(),0);
        final var view=new RecipeBrowserView[1];
        final var scene=new Scene[1];
        try(final var tasks=new EditorTasks()) {
            FxLayoutTestSupport.run(()->{
                view[0]=new RecipeBrowserView(discovery,editor,tasks,(recipe,format)->{},integration);
                scene[0]=new Scene(view[0],1240,760);
                new UiTheme().apply(scene[0]);
                view[0].runtime(catalog,discovery,List.of());
                view[0].showRuntime();
                ((TextField)view[0].lookup("#recipe-search")).setText(desh.toString());
                view[0].applyCss();view[0].layout();
            });
            try {
                await(()->{
                    view[0].applyCss();view[0].layout();
                    final var table=(TableView<?>)view[0].lookup("#recipe-table");
                    return table.getItems().size()==1&&view[0].lookup(".recipe-layout")!=null;
                });
                await(()->!view[0].lookupAll(".image-view").isEmpty() || view[0].lookupAll(".inventory-preview").stream()
                        .anyMatch(node->"PREVIEW_AVAILABLE".equals(node.getProperties().get("inventory.preview.status"))));
                final var image=new WritableImage[1];
                FxLayoutTestSupport.run(()->image[0]=scene[0].snapshot(null));
                final var pixels=new BufferedImage((int)image[0].getWidth(),(int)image[0].getHeight(),BufferedImage.TYPE_INT_ARGB);
                final var reader=image[0].getPixelReader();
                for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)pixels.setRGB(x,y,reader.getArgb(x,y));
                final var directory=Files.createDirectories(Path.of("build","phase24-unified"));
                ImageIO.write(pixels,"PNG",directory.resolve("offline-desh.png").toFile());
            } finally { FxLayoutTestSupport.run(view[0]::close); }
        }
    }

    private void await(final BooleanSupplier condition) throws Exception {
        final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(40);
        final var completed=new boolean[1];
        while(!completed[0]&&System.nanoTime()<deadline) {
            FxLayoutTestSupport.run(()->completed[0]=condition.getAsBoolean());
            if(!completed[0])Thread.sleep(100);
        }
        assertTrue(completed[0],"Timed out waiting for the offline view to render");
    }
}
