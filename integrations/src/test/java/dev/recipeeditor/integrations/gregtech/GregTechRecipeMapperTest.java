package dev.recipeeditor.integrations.gregtech;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.registry.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class GregTechRecipeMapperTest {
    private final String json="""
        {"type":"gtceu:assembler","duration":100,"category":"gtceu:assembler",
         "inputs":{"item":[{"content":{"type":"gtceu:sized","count":2,"ingredient":{"tag":"forge:ingots/iron"}},"chance":0}],
                   "fluid":[{"content":{"amount":100,"value":[{"tag":"forge:water"}]}}]},
         "outputs":{"item":[{"content":{"item":"minecraft:iron_ingot"},"chance":5000,"maxChance":10000,"tierChanceBoost":200}],
                    "fluid":[{"content":{"amount":20,"value":{"fluid":"minecraft:water"}}}]},
         "tickInputs":{"eu":[{"content":30}]},"recipeConditions":[{"type":"temperature","temperature":1200}],
         "unknown":{"nested":[1,2]},"data":{"circuit":4}}
        """;
    private GregTechRecipeDefinition model(final String raw) { return new GregTechRecipeMapper().map(ResourceLocation.parse("test:gt"),raw,false); }
    @Test void typedInputsOutputsEnergyChanceAndConditions() {
        final var value=model(json);assertEquals(100,value.duration().orElseThrow());assertEquals(30,value.inputEUt().orElseThrow());assertTrue(value.outputEUt().isEmpty());
        final var input=value.contents().getFirst();assertTrue(input.nonConsumable());assertInstanceOf(IngredientDefinition.Sized.class,input.ingredient());
        final var sized=(IngredientDefinition.Sized)input.ingredient();assertInstanceOf(IngredientDefinition.Tag.class,sized.ingredient());assertEquals(2,sized.amount());
        assertTrue(value.contents().stream().anyMatch(c->c.ingredient() instanceof IngredientDefinition.Fluid f && f.tag()));
        final var chance=value.contents().stream().filter(c->c.role().equals("outputs")&&c.capability().equals("item")).findFirst().orElseThrow();assertEquals(5000,chance.chance().orElseThrow());assertEquals(200,chance.tierChanceBoost().orElseThrow());
        assertEquals(1,value.conditions().size());assertTrue(value.additionalProperties().containsKey("unknown"));assertEquals(json,value.rawJson());
    }
    @Test void patchChangesOnlySelectedLeafAndKeepsUnknownState() {
        final var changed=new GregTechRecipeEditing().replace(json,"/duration","240");
        final var original=new GregTechRecipeMapper().object(json);final var updated=new GregTechRecipeMapper().object(changed);
        assertEquals(original.get("unknown"),updated.get("unknown"));assertEquals(original.get("inputs"),updated.get("inputs"));assertEquals(original.get("recipeConditions"),updated.get("recipeConditions"));assertEquals(240,model(changed).duration().orElseThrow());
        assertThrows(IllegalArgumentException.class,()->new GregTechRecipeEditing().replace(json,"/missing","1"));
    }
    @Test void invalidValuesRejectSaveWithoutUniversalSlotLimits() {
        assertTrue(new GregTechRecipeValidator().validate(model(json.replace("\"duration\":100","\"duration\":0"))).stream().anyMatch(i->i.severity()==ValidationIssue.Severity.ERROR));
        assertTrue(new GregTechRecipeValidator().validate(model(json)).stream().noneMatch(i->i.severity()==ValidationIssue.Severity.ERROR));
        assertTrue(new GregTechRecipeValidator().validate(model(json.replace("[{\"tag\":\"forge:water\"}]","[]"))).stream().anyMatch(i->i.severity()==ValidationIssue.Severity.ERROR));
        assertThrows(IllegalArgumentException.class,()->model(json.replace("\"duration\":100","\"duration\":1.5")));
    }
    @Test void unknownGtSchemaKeepsRawEditingAndConfirmedRuntimeIdsRejectCreate() {
        final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of("gtceu"),Path.of("test"),"data/test/recipes/unknown.json","unit fixture");
        final var validator=new RecipeValidator(new ProjectRecipeParsers().parsers(),new RegistryIndex(List.of()));
        final String unknown="{\"type\":\"gtceu:unrecognized\",\"custom\":{\"keep\":true}}";
        final var result=validator.validate("test:unknown",unknown,source);assertTrue(result.valid());assertEquals(unknown,result.recipe().rawJson());assertEquals(RecipeParseStatus.UNSUPPORTED,result.recipe().status());
        final var editor=new RecipeEditorService(new RecipeIndex(List.of(),new TagIndex(List.of())),validator);
        final var definition=validator.validate("test:gt",json,source).recipe();
        editor.applyRuntimeEvidence(new RegistryIndex(List.of()),new TagIndex(List.of()),Set.of(),Set.of(definition.id()),"unit runtime fixture");
        assertThrows(IllegalArgumentException.class,()->editor.create(definition));assertTrue(editor.snapshot().changes().isEmpty());
    }
    @Test void existingEditorHandlesTypedGtUpdatesUndoAndInvalidSave() {
        final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of("gtceu"),Path.of("test"),"data/test/recipes/gt.json","unit fixture");
        final var validator=new RecipeValidator(new ProjectRecipeParsers().parsers(),new RegistryIndex(List.of()));
        final var definition=validator.validate("test:gt",json,source).recipe();
        final var index=new RecipeIndex(List.of(definition),new TagIndex(List.of()));
        assertEquals(1,index.findRecipesProducing(ResourceLocation.parse("minecraft:iron_ingot")).size());
        assertTrue(index.findRecipesUsing(ResourceLocation.parse("minecraft:water")).isEmpty(),"Fluids must not appear in item lookup");
        final var editor=new RecipeEditorService(new RecipeIndex(List.of(definition),new TagIndex(List.of())),validator);
        final var updated=validator.validate("test:gt",new GregTechRecipeEditing().replace(json,"/duration","240"),source).recipe();editor.update(definition.id(),updated);assertEquals(RecipeModificationStatus.MODIFIED,editor.status(definition.id()));editor.undo();assertEquals(RecipeModificationStatus.UNCHANGED,editor.status(definition.id()));editor.redo();assertEquals(RecipeModificationStatus.MODIFIED,editor.status(definition.id()));
        assertFalse(validator.validate("test:gt",json.replace("\"duration\":100","\"duration\":0"),source).valid());
    }
}
