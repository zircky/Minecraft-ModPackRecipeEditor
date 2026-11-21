package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeProvenanceTest {
    @Test void mismatchedChangeKeyCannotAssignAnotherRecipesSources() {
        final var recipe=RecipeFixtures.recipe("test:one","one.jar",List.of(),List.of(),RecipeParseStatus.UNSUPPORTED);
        final var change=new RecipeChange(recipe.id(),RecipeChangeType.CREATE,List.of(),List.of(recipe),java.time.Instant.EPOCH,Map.of());
        assertThrows(IllegalArgumentException.class,()->new RecipeChangeSet(Map.of(ResourceLocation.parse("test:two"),change)));
    }
    @Test void editUndoRedoAndProjectReopenKeepEveryOriginalAndReplacementOrigin() {
        final var id=ResourceLocation.parse("minecraft:tnt");
        final var source=new RecipeSource(SourceType.MOD_JAR,List.of("biomesoplenty"),Path.of("bop.jar"),"data/minecraft/recipes/tnt.json","Static resource scan");
        final var datapack=new RecipeSource(SourceType.DATAPACK,List.of(),Path.of("custom-pack"),source.internalPath(),"Local scan");
        final var validator=new RecipeValidator(List.of(),new RegistryIndex(List.of()));
        final String raw="{\"type\":\"custom:recipe\",\"value\":1}";
        final var first=validator.validate(id,raw,source).recipe();final var second=validator.validate(id,raw,datapack).recipe();
        final var originals=new RecipeIndex(List.of(first,second),new TagIndex(List.of()));
        final var editor=new RecipeEditorService(originals,validator);
        editor.update(id,validator.validate(id,raw.replace(":1",":2"),source).recipe());
        final var saved=editor.snapshot();assertEquals(2,saved.changes().get(id).originals().size());
        editor.undo();assertEquals(2,editor.effective().findById(id).size());editor.redo();assertEquals(saved,editor.snapshot());
        final var project=Path.of("project.json").toAbsolutePath();
        final var document=ProjectDocument.capture(Path.of("pack").toAbsolutePath(),saved);
        final var reopened=document.materialize(validator,project);
        assertEquals(saved,reopened);assertEquals(source,reopened.changes().get(id).replacement().getFirst().source());
        final var resolved=new RecipeSourceResolver().project(id,reopened.changes().get(id).originals().stream().map(OriginalRecipeReference::source).toList(),List.of(source),project,false,false);
        assertEquals(Set.of(source,datapack),Set.copyOf(resolved.overriddenDefinitions()));
        assertEquals(project,resolved.activeDefinition().orElseThrow().archivePath());
        assertEquals(ProvenanceConfidence.PROJECT_OVERLAY,resolved.confidence());assertTrue(resolved.activeSource().contains("runtime winner UNKNOWN"));
        assertEquals(2,originals.findById(id).size());
    }
    @Test void runtimeObservationNeverInheritsCandidateModIds() {
        final var id=ResourceLocation.parse("minecraft:tnt");
        final var candidate=new RecipeSource(SourceType.MOD_JAR,List.of("biomesoplenty"),Path.of("bop.jar"),"data/minecraft/recipes/tnt.json","scan");
        final var runtime=new RecipeSource(SourceType.RUNTIME,List.of("arbitrary"),Path.of("snapshot.mresnapshot"),"","Companion");
        final var evidence=new RecipeSourceResolver().runtime(id,List.of(candidate),runtime);
        assertTrue(evidence.sourceModIds().isEmpty());assertEquals(Set.of("biomesoplenty"),evidence.candidateSourceModIds());
        assertEquals("UNKNOWN",evidence.declaringSource());assertEquals("UNKNOWN",runtime.originalFile());
        assertEquals(List.of(candidate),evidence.discoveredDefinitions());assertTrue(evidence.overriddenDefinitions().isEmpty());
    }
    @Test void legacyProjectKeepsAllBaseReferencesWithoutChoosingOneAsReplacementOrigin() {
        final var id=ResourceLocation.parse("minecraft:tnt");final String raw="{\"type\":\"custom:recipe\"}";
        final var first=new RecipeSource(SourceType.MOD_JAR,List.of("biomesoplenty"),Path.of("bop.jar"),"data/minecraft/recipes/tnt.json","scan");
        final var second=new RecipeSource(SourceType.DATAPACK,List.of(),Path.of("pack"),first.internalPath(),"scan");
        final var references=List.of(new OriginalRecipeReference(first,"a".repeat(64)),new OriginalRecipeReference(second,"b".repeat(64)));
        final var stored=new StoredRecipeChange(id,RecipeChangeType.UPDATE,references,List.of(raw),java.time.Instant.EPOCH,Map.of());
        final var file=Path.of("project.json").toAbsolutePath();
        final var changes=new ProjectDocument(1,Path.of("instance").toAbsolutePath(),List.of(stored)).materialize(new RecipeValidator(List.of(),new RegistryIndex(List.of())),file);
        final var change=changes.changes().get(id);assertEquals(references,change.originals());
        assertEquals(file,change.replacement().getFirst().source().archivePath());
        assertTrue(change.replacement().getFirst().source().sourceModIds().isEmpty());
        assertEquals("UNKNOWN",change.replacement().getFirst().source().declaringSource());
    }
}
