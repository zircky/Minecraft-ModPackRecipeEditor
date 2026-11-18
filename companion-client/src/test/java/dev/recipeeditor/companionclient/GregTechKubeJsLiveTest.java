package dev.recipeeditor.companionclient;
import dev.recipeeditor.integrations.gregtech.*;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in: actual GT state -> existing editor -> KubeJS -> restarted Minecraft -> fresh runtime DTOs. */
@org.junit.jupiter.api.Tag("external")
final class GregTechKubeJsLiveTest {
    @Test @EnabledIfSystemProperty(named="phase7Mode",matches="prepare|baseline|modified")
    void realGtEditingExportAndRestart() throws Exception {
        final var directory=Path.of(System.getProperty("phase7Directory")).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        final String mode=System.getProperty("phase7Mode");
        if(mode.equals("prepare")){prepare(directory);return;}
        final var ready=Path.of(System.getProperty("runtimeReadyFlag"));
        final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(240);
        while(!Files.isRegularFile(ready)||!Files.readString(ready).trim().equals(System.getProperty("runtimeProbeRunId"))){
            if(System.nanoTime()>deadline)throw new AssertionError("Fresh Minecraft world readiness timeout");Thread.sleep(250);
        }
        try(final var client=new CompanionConnectionService()) {
            client.connect(Path.of(System.getProperty("runtimeSessionFile"))).get(10,TimeUnit.SECONDS);
            final var snapshot=client.synchronize(progress->{}).get(130,TimeUnit.SECONDS);
            assertTrue(snapshot.mods().stream().anyMatch(m->m.id().equals("gtceu")&&m.version().equals("7.1.4")));
            assertTrue(snapshot.mods().stream().anyMatch(m->m.id().equals("kubejs")&&m.version().equals("2001.6.4-build.120")));
            for(final String name:List.of("updated","created","deleted")) {
                final var found=snapshot.recipes().stream().filter(r->r.id().equals(readId(directory,name))).toList();
                if(mode.equals("modified")&&name.equals("deleted")){assertTrue(found.isEmpty(),"DELETE was not applied");continue;}
                if(mode.equals("baseline")&&name.equals("created")){assertTrue(found.isEmpty());continue;}
                assertEquals(1,found.size(),"Expected one runtime recipe: "+name);
                final var actual=new GregTechRecipeMapper().map(found.getFirst());
                final var expected=new GregTechRecipeMapper().map(ResourceLocation.parse(readId(directory,name)),Files.readString(directory.resolve(mode+"-"+name+".json")),false);
                assertEquals(expected.duration(),actual.duration());assertEquals(expected.inputEUt(),actual.inputEUt());
                // Exact capability content comparison includes fluid/item quantities, chance, boost, nonconsumable semantics.
                final var clean=new GregTechRuntimeDraft().copy(found.getFirst());
                final var cleanRoot=new GregTechRecipeMapper().object(clean);final var expectedRoot=new GregTechRecipeMapper().object(expected.rawJson());
                for(final String field:List.of("inputs","outputs","tickInputs","tickOutputs"))assertEquals(expectedRoot.get(field),cleanRoot.get(field),"Loaded GT field differs: "+field);
            }
            new RuntimeSnapshotService().save(directory.resolve(mode+".mresnapshot"),snapshot);
            Files.writeString(directory.resolve(mode+"-result.txt"),"Actual Minecraft 1.20.1 Forge "+snapshot.environment().loaderVersion()+"; GTCEu 7.1.4; KubeJS 2001.6.4-build.120\nMode="+mode+"\nExpected recipe state for this mode checked after actual startup; content, fluids, EU/t, duration and available chance/non-consumable properties compared.\nRecipes="+snapshot.recipes().size()+"\n");
            Files.writeString(Path.of(System.getProperty("runtimeProbeMarker")),"Phase 7 actual KubeJS assertions passed");
        }
    }
    private String readId(final Path directory,final String name) {
        try{return Files.readString(directory.resolve(name+".id")).trim();}catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    private void prepare(final Path directory) throws Exception {
        final var snapshot=new RuntimeSnapshotService().load(Path.of(System.getProperty("runtimeSnapshotFile")));
        final var mapper=new GregTechRecipeMapper();final var exporter=new GregTechRecipeExporter();
        final var candidates=snapshot.recipes().stream().filter(r->r.type().equals("gtceu:assembler")&&r.adapter().equals("gtceu-7.1.4")&&!r.properties().has("runtimeVariants"))
                .filter(r->{try{return exporter.unsupported(mapper.map(ResourceLocation.parse(r.id()),new GregTechRuntimeDraft().copy(r),false)).isEmpty();}catch(RuntimeException invalid){return false;}}).limit(2).toList();
        assertEquals(2,candidates.size());
        final var validator=new RecipeValidator(new ProjectRecipeParsers().parsers(),new RegistryIndex(List.of()));
        final var definitions=new ArrayList<RecipeDefinition>();
        for(int i=0;i<2;i++) {
            final String name=i==0?"updated":"deleted";final var runtime=candidates.get(i);
            Files.writeString(directory.resolve(name+".id"),runtime.id());
            final String raw=new GregTechRuntimeDraft().copy(runtime);
            Files.writeString(directory.resolve("baseline-"+name+".json"),raw);
            final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of("gtceu"),directory,"baseline-"+name+".json","Explicit test fixture copied from real runtime; original source UNKNOWN");
            definitions.add(validator.validate(runtime.id(),raw,source).recipe());
        }
        Files.writeString(directory.resolve("created.id"),"mre_phase7_test:assembler/created");
        Files.writeString(directory.resolve("baseline.js"),"// Phase 7 baseline: original GT recipes, no modifications.\n");
        final var editor=new RecipeEditorService(new RecipeIndex(definitions,new TagIndex(List.of())),validator);
        final String initial=Files.readString(directory.resolve("baseline-updated.json"));
        final String updated=new GregTechRecipeEditing().replace(initial,"/duration","137");
        final var root=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.object(updated);
        root.withObject("/tickInputs").putArray("eu").addObject().put("content",11).put("chance",10000).put("maxChance",10000).put("tierChanceBoost",0);
        ((com.fasterxml.jackson.databind.node.ObjectNode)root.path("inputs").path("item").get(0)).put("chance",0);
        final var output=(com.fasterxml.jackson.databind.node.ObjectNode)root.path("outputs").path("item").get(0);output.put("chance",8000);output.put("maxChance",10000);output.put("tierChanceBoost",200);
        final String modified=root.toPrettyString();
        editor.update(definitions.getFirst().id(),validator.validate(definitions.getFirst().id(),modified,definitions.getFirst().source()).recipe());
        editor.delete(definitions.get(1).id());
        final var created=(com.fasterxml.jackson.databind.node.ObjectNode)root.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)created.path("inputs").path("item").get(0).path("content").path("ingredient")).put("item","minecraft:copper_ingot");
        editor.create(validator.validate(readId(directory,"created"),created.toPrettyString(),definitions.getFirst().source()).recipe());
        final var finalScript=new StringBuilder("// Phase 7 test: genuine pre-existing GT recipe IDs, not same-event additions.\nServerEvents.recipes(event => {\n");
        for(final var change:editor.snapshot().changes().values()) {
            if(change.type()==RecipeChangeType.DELETE)finalScript.append("  event.remove({id: '").append(change.id()).append("'});\n");
            else {
                final var definition=change.replacement().getFirst();final String name=change.type()==RecipeChangeType.CREATE?"created":"updated";
                Files.writeString(directory.resolve("modified-"+name+".json"),definition.rawJson());
                finalScript.append(exporter.generate(mapper.map(definition),definition.id().toString(),change.type()==RecipeChangeType.UPDATE));
            }
        }
        finalScript.append("});\n");Files.writeString(directory.resolve("modified.js"),finalScript);
        assertEquals(3,editor.snapshot().changes().size());assertTrue(finalScript.toString().contains("tierChanceBoost"));
    }
}
