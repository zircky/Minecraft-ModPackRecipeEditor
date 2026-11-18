package dev.recipeeditor.integrations.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeEditorServiceTest {
    @Test void createUpdateDeleteDuplicateAndIndependentOriginals() {
        final var service=EditorFixtures.service(); final var id=ResourceLocation.parse("test:new");
        service.create(EditorFixtures.recipe(id.toString(),EditorFixtures.RAW)); assertEquals(RecipeModificationStatus.CREATED,service.status(id));
        service.update(id,EditorFixtures.recipe(id.toString(),EditorFixtures.RAW.replace("test:ingot","test:other")));
        assertEquals(1,service.effective().findRecipesProducing(ResourceLocation.parse("test:other")).size());
        assertEquals(1,service.effective().findRecipesProducing(ResourceLocation.parse("test:ingot")).size());
        final var copy=service.duplicate(service.effective().findById(id).getFirst(),ResourceLocation.parse("test:copy"));
        assertEquals("test:copy",copy.id().toString()); service.delete(id); assertTrue(service.effective().findById(id).isEmpty());
        assertEquals(1,service.effective().findById(copy.id()).size());
        assertEquals(EditorFixtures.RAW,service.originals().findById(ResourceLocation.parse("test:base")).getFirst().rawJson());
    }
    @Test void idConflictsAndFailedValidationDoNotDamageState() {
        final var service=EditorFixtures.service();
        assertThrows(IllegalArgumentException.class,()->service.create(EditorFixtures.recipe("test:base",EditorFixtures.RAW)));
        assertThrows(IllegalArgumentException.class,()->service.update(ResourceLocation.parse("test:base"),EditorFixtures.recipe("test:base","{}")));
        assertTrue(service.snapshot().changes().isEmpty()); assertFalse(service.canUndo());
    }
    @Test void overlappingOriginalsRemainAccessibleButOverrideIsUnique() {
        final var first=EditorFixtures.recipe("test:base",EditorFixtures.RAW); final var second=EditorFixtures.recipe("test:base",EditorFixtures.RAW.replace("0.5","0.7"));
        final var service=new RecipeEditorService(new RecipeIndex(List.of(first,second),new TagIndex(List.of())),EditorFixtures.validator());
        service.update(first.id(),EditorFixtures.recipe("test:base",EditorFixtures.RAW.replace("0.5","0.9")));
        assertEquals(2,service.originals().findById(first.id()).size()); assertEquals(1,service.effective().findById(first.id()).size()); assertEquals(2,service.snapshot().changes().get(first.id()).originals().size());
    }
    @Test void undoRedoAndNewCommandClearRedo() {
        final var service=EditorFixtures.service(); final var id=ResourceLocation.parse("test:base");
        service.delete(id); service.undo(); assertEquals(1,service.effective().findById(id).size()); service.redo(); assertTrue(service.effective().findById(id).isEmpty());
        service.undo(); service.update(id,EditorFixtures.recipe(id.toString(),EditorFixtures.RAW.replace("0.5","0.9"))); assertFalse(service.canRedo());
    }
}
