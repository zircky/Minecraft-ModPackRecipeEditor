package dev.recipeeditor.companionclient;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeMergeServiceTest {
    @TempDir Path root;
    private final String raw="{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"minecraft:iron_ingot\",\"cookingtime\":200}";
    private RecipeValidator validator(){return new RecipeValidator(new Vanilla1201Parsers().parsers(),new RegistryIndex(List.of()));}
    private RecipeDefinition recipe(String id,String json){return validator().validate(id,json,new RecipeSource(SourceType.MOD_JAR,List.of("owner"),root.resolve("mod.jar"),"data/test/recipes/smelt.json","static fixture")).recipe();}
    private RecipeIndex index(List<RecipeDefinition> recipes){return new RecipeIndex(recipes,new TagIndex(List.of()));}
    @Test void matchingKnownFieldsPreserveBothSourcesWithoutClaimingOriginalJson(){final var original=recipe("test:smelt",raw);final var result=new RecipeMergeService().merge(index(List.of(original)),new RuntimeFixtures().snapshot(root,"s",1));assertTrue(result.changedOrMissing().isEmpty());assertEquals(List.of(original),result.entries().getFirst().staticDefinitions());assertNotNull(result.entries().getFirst().runtime());assertTrue(result.entries().getFirst().diagnostics().stream().anyMatch(d->d.contains("not proven")));}
    @Test void changedOutputAndMissingRecipeAreDiagnosed(){final var result=new RecipeMergeService().merge(index(List.of(recipe("test:smelt",raw.replace("minecraft:iron_ingot","minecraft:gold_ingot")),recipe("test:missing",raw))),new RuntimeFixtures().snapshot(root,"s",1));assertEquals(Set.of(ResourceLocation.parse("test:smelt"),ResourceLocation.parse("test:missing")),result.changedOrMissing());}
    @Test void unknownAndPartialRuntimeKeepMetadataAndWarning(){final var base=new RuntimeFixtures().snapshot(root,"s",1);final var unknown=new RuntimeData.Recipe("unknown:recipe","unknown:type","unknown:serializer","Unknown","generic",RuntimeData.Support.UNSUPPORTED,new com.google.gson.JsonObject(),"runtime",List.of("No adapter"));final var snapshot=new RuntimeData.Snapshot(1,"partialRecipe",true,base.environment(),base.registries(),base.tags(),List.of(unknown),base.mods(),List.of());final var result=new RecipeMergeService().merge(index(List.of()),snapshot);assertEquals(unknown,result.entries().getFirst().runtime());assertFalse(result.entries().getFirst().diagnostics().isEmpty());}
    @Test void runtimeConflictQuarantinesWithoutLosingUnsavedOrPersistedChanges(){final var original=recipe("test:smelt",raw);final var editor=new RecipeEditorService(index(List.of(original)),validator());editor.delete(original.id());final var persisted=editor.persistentSnapshot();editor.markPersisted(persisted);editor.create(recipe("test:unsaved",raw));final var before=editor.persistentSnapshot();final var snapshot=new RuntimeFixtures().snapshot(root,"s",1);final var registry=new RegistryMergeService().merge(new RegistryIndex(List.of()),snapshot,root.resolve("snapshot"));editor.applyRuntimeEvidence(registry.preferred(),registry.runtimeTags(),Set.of(original.id()),Set.of(original.id()),"test runtime");assertEquals(before,editor.persistentSnapshot());assertEquals(persisted,editor.persistedSnapshot());assertTrue(editor.dirty());assertTrue(editor.hasConflict(original.id()));assertTrue(editor.snapshot().changes().containsKey(ResourceLocation.parse("test:unsaved")));assertFalse(editor.canUndo());}
    @Test void craftingRecipeTypeAndSerializerAreComparedWithoutFalseConflicts()throws Exception {
        final var base=new RuntimeFixtures().snapshot(root,"session",1);final String json="""
            {"type":"minecraft:crafting_shaped","pattern":["A"],"key":{"A":{"item":"minecraft:iron_ingot"}},"result":{"item":"minecraft:iron_block"}}
            """;
        final var original=recipe("test:shaped",json);final var properties=new com.google.gson.JsonObject();properties.add("ingredients",com.google.gson.JsonParser.parseString("""
            [{"item":"minecraft:iron_ingot"}]
            """));
        final var runtime=new RuntimeData.Recipe("test:shaped","minecraft:crafting","minecraft:crafting_shaped","ShapedRecipe","vanilla-1.20.1",RuntimeData.Support.SUPPORTED,properties,"runtime fixture",List.of());
        final var snapshot=new RuntimeData.Snapshot(1,"crafting",true,base.environment(),base.registries(),base.tags(),List.of(runtime),base.mods(),List.of());
        assertTrue(new RecipeMergeService().merge(index(List.of(original)),snapshot).changedOrMissing().isEmpty());
    }
}
