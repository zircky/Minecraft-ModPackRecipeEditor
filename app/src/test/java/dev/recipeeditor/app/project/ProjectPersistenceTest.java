package dev.recipeeditor.app.project;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("integration")
final class ProjectPersistenceTest {
    @TempDir Path root;
    private final String raw="{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"test:ore\"},\"result\":\"test:ingot\"}";
    private RecipeValidator validator() {return new RecipeValidator(new Vanilla1201Parsers().parsers(),new RegistryIndex(List.of()));}
    private RecipeDefinition recipe(final String id,final String raw) {return validator().validate(id,raw,new RecipeSource(SourceType.MOD_JAR,List.of("test"),root.resolve("source/base.jar"),"data/test/recipes/base.json","Static resource scan")).recipe();}
    private RecipeEditorService editor(final String base) {return new RecipeEditorService(new RecipeIndex(List.of(recipe("test:base",base)),new TagIndex(List.of())),validator());}
    private Path source() throws Exception {final var path=root.resolve("source");Files.createDirectories(path);return path;}
    @Test void saveLoadRestoreAndPreviousGoodBackup() throws Exception {
        final var source=source(); Files.writeString(source.resolve("base.jar"),"unchanged sentinel"); final var editor=editor(raw); final var id=ResourceLocation.parse("test:base");
        editor.update(id,recipe(id.toString(),raw.replace("test:ingot","test:changed")));
        final var file=root.resolve("project.json");
        try(final var store=new FileProjectPersistence()) {
            store.write(file,ProjectDocument.capture(source,editor.persistentSnapshot())); final var saved=Files.readAllBytes(file);
            final var restored=editor(raw); restored.restore(store.read(file).materialize(validator(),file));
            assertTrue(restored.effective().findById(id).getFirst().rawJson().contains("test:changed")); assertFalse(restored.dirty());
            editor.create(recipe("test:new",raw)); store.write(file,ProjectDocument.capture(source,editor.persistentSnapshot()));
            assertArrayEquals(saved,Files.readAllBytes(root.resolve("project.json.bak"))); assertEquals("unchanged sentinel",Files.readString(source.resolve("base.jar")));
        }
    }
    @Test void gregTechUnknownStateSurvivesExistingProjectFormat() throws Exception {
        final var source=source();final var file=root.resolve("gt-project.json");
        final var validator=new RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),new RegistryIndex(List.of()));
        final String gt="""
            {"type":"gtceu:assembler","duration":120,
             "inputs":{"fluid":[{"content":{"amount":1000,"value":[{"tag":"forge:water"}]}}]},
             "outputs":{"item":[{"content":{"item":"minecraft:iron_ingot"},"chance":5000,"maxChance":10000,"tierChanceBoost":100}]},
             "recipeConditions":[{"type":"custom_condition","extra":{"preserve":[1,2]}}],"unknown":{"keep":true}}
            """;
        final var editor=new RecipeEditorService(new RecipeIndex(List.of(),new TagIndex(List.of())),validator);
        final var definition=validator.validate("test:assembler/new",gt,new RecipeSource(SourceType.LOCAL_RESOURCE,List.of("gtceu"),source,"test.json","unit fixture")).recipe();
        editor.create(definition);
        try(final var store=new FileProjectPersistence()) {
            store.write(file,ProjectDocument.capture(source,editor.persistentSnapshot()));
            final var restored=new RecipeEditorService(new RecipeIndex(List.of(),new TagIndex(List.of())),validator);
            restored.restore(store.read(file).materialize(validator,file));
            assertEquals(gt,restored.effective().findById(definition.id()).getFirst().rawJson());assertFalse(restored.dirty());
            assertInstanceOf(IngredientDefinition.Fluid.class,restored.effective().findById(definition.id()).getFirst().inputs().getFirst());
        }
    }
    @Test void corruptedOrFutureFormatIsRejected() throws Exception {
        final var source=source(); final var file=root.resolve("project.json");
        try(final var store=new FileProjectPersistence()) {
            store.write(file,ProjectDocument.capture(source,new RecipeChangeSet())); final String good=Files.readString(file);
            Files.writeString(file,good.replace(source.toString().replace("\\","\\\\"),"changed")); assertThrows(java.io.IOException.class,()->store.read(file));
            Files.writeString(file,good.replace("\"formatVersion\" : 1","\"formatVersion\" : 99")); assertThrows(java.io.IOException.class,()->store.read(file));
            Files.writeString(file,"not JSON"); assertThrows(java.io.IOException.class,()->store.read(file));
        }
    }
    @Test void failedSerializationDoesNotOverwriteWorkingFile() throws Exception {
        final var source=source(); final var file=root.resolve("project.json"); final var editor=editor(raw); editor.create(recipe("test:new",raw));
        try(final var store=new FileProjectPersistence()) {
            store.write(file,ProjectDocument.capture(source,editor.snapshot())); final var before=Files.readAllBytes(file);
            final var value=ProjectDocument.capture(source,editor.snapshot()).changes().getFirst();
            final var invalid=new StoredRecipeChange(value.id(),value.type(),value.originals(),List.of("bad JSON"),value.timestamp(),value.metadata());
            assertThrows(java.io.IOException.class,()->store.write(file,new ProjectDocument(1,source,List.of(invalid)))); assertArrayEquals(before,Files.readAllBytes(file));
        }
    }
    @Test void changedOriginalIsBlockedAndSavedChangePreserved() throws Exception {
        final var source=source(); final var file=root.resolve("project.json"); final var edited=editor(raw); edited.delete(ResourceLocation.parse("test:base"));
        try(final var store=new FileProjectPersistence()) {
            store.write(file,ProjectDocument.capture(source,edited.snapshot())); final var current=editor(raw.replace("test:ingot","test:updated_mod"));
            current.restore(store.read(file).materialize(validator(),file)); assertEquals(1,current.conflicts().size()); assertEquals(1,current.effective().findById(ResourceLocation.parse("test:base")).size());
            store.write(file,ProjectDocument.capture(source,current.persistentSnapshot())); assertEquals(1,store.read(file).changes().size());
        }
    }
    @Test void savingInsideModpackIsRejected() throws Exception {
        final var source=source();try(final var store=new FileProjectPersistence()) {assertThrows(java.io.IOException.class,()->store.write(source.resolve("project.json"),ProjectDocument.capture(source,new RecipeChangeSet())));}
    }
    @Test void projectLimitsAndDuplicateIdsAreChecked() throws Exception {
        final var source=source();final var editor=editor(raw);editor.create(recipe("test:new",raw)); final var change=ProjectDocument.capture(source,editor.snapshot()).changes().getFirst();
        assertThrows(IllegalArgumentException.class,()->new ProjectDocument(1,source,List.of(change,change)));
        assertThrows(IllegalArgumentException.class,()->new ProjectDocument(2,source,List.of()));
    }
    @Test void registeredFormatMigrationIsExplicitAndDoesNotRewriteInput() throws Exception {
        final var source=source();final var file=root.resolve("legacy.json");
        try(final var store=new FileProjectPersistence()) {
            store.write(file,ProjectDocument.capture(source,new RecipeChangeSet()));
            final var mapper=new com.fasterxml.jackson.databind.ObjectMapper();final var envelope=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(Files.readString(file));envelope.put("formatVersion",0);Files.writeString(file,envelope.toString());
            assertThrows(java.io.IOException.class,()->store.read(file));final var before=Files.readAllBytes(file);
            final var migration=new ProjectFormatMigration(){public int fromVersion(){return 0;}public int toVersion(){return 1;}public com.fasterxml.jackson.databind.JsonNode migrate(final com.fasterxml.jackson.databind.JsonNode value){return value.deepCopy();}};
            try(final var migrated=new FileProjectPersistence(List.of(migration))) {assertEquals(1,migrated.read(file).version());}
            assertArrayEquals(before,Files.readAllBytes(file));
        }
    }
}
