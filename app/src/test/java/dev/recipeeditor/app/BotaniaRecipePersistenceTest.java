package dev.recipeeditor.app;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.exporters.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external") @EnabledIfSystemProperty(named="realModpack",matches=".+")
final class BotaniaRecipePersistenceTest {
    @TempDir Path root;
    @Test void genericChangeSetRoundtripPreservesBrewReferenceUnknownJsonAndProvenance()throws Exception {
        // Exercises the existing document pipeline, not a claim that Botania UI authoring is enabled.
        try(final var artifact=new BotaniaTestArtifact();final var persistence=new dev.recipeeditor.app.project.FileProjectPersistence()) {
            final var original=artifact.recipe("data/botania/recipes/brew/allure.json");final var index=new RecipeIndex(List.of(original),new TagIndex(List.of()));final var editor=new RecipeEditorService(index,artifact.validator);
            final String changed=new RecipeFormService().patch(original.rawJson(),"phase16_test_extension","{\"preserve\":true}");
            editor.update(original.id(),artifact.validator.validate(original.id(),changed,original.source()).recipe());editor.undo();assertTrue(editor.snapshot().changes().isEmpty());editor.redo();assertEquals(1,editor.snapshot().changes().size());
            final var project=root.resolve("botania-roundtrip.json");persistence.write(project,ProjectDocument.capture(Path.of(System.getProperty("realModpack")),editor.snapshot()));final var reopened=persistence.read(project).materialize(artifact.validator,project);final var replacement=reopened.changes().get(original.id()).replacement().getFirst();
            assertEquals(changed,replacement.rawJson());assertEquals(original.source(),replacement.source());assertEquals("botania:allure",assertInstanceOf(RecipeOutput.Brew.class,replacement.outputs().getFirst()).definition().id().toString());
        }
    }
    @Test void genuineSavedRuntimeSnapshotMergeRetainsStaticBrewAndItsProvenance()throws Exception {
        try(final var artifact=new BotaniaTestArtifact()) {
            final var original=artifact.recipe("data/botania/recipes/brew/allure.json");final var index=new RecipeIndex(List.of(original),new TagIndex(List.of()));
            final var path=Path.of(System.getProperty("architectureRoot")).resolve(".manual-tests/resources/isg-materials.mresnapshot");
            final var snapshot=new dev.recipeeditor.companionclient.RuntimeSnapshotService().load(path);
            final var merged=new dev.recipeeditor.companionclient.RecipeMergeService().merge(index,snapshot);final var entry=merged.entries().stream().filter(e->e.id().equals(original.id())).findFirst().orElseThrow();
            assertEquals(List.of(original),entry.staticDefinitions());assertInstanceOf(RecipeOutput.Brew.class,entry.staticDefinitions().getFirst().outputs().getFirst());
            assertEquals(original.source().archivePath(),entry.staticDefinitions().getFirst().source().archivePath());assertTrue(snapshot.complete());
        }
    }
}
