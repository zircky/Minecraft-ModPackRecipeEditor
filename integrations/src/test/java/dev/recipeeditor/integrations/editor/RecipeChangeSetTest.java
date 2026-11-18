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
final class RecipeChangeSetTest {
    @Test void createThenDeleteCollapsesAndUndoRestoresCreate() {
        final var service=EditorFixtures.service(); final var id=ResourceLocation.parse("test:new"); service.create(EditorFixtures.recipe(id.toString(),EditorFixtures.RAW)); service.delete(id);
        assertTrue(service.snapshot().changes().isEmpty()); service.undo(); assertEquals(RecipeChangeType.CREATE,service.snapshot().changes().get(id).type());
    }
    @Test void updateResetDeleteRestore() {
        final var service=EditorFixtures.service(); final var id=ResourceLocation.parse("test:base");
        service.update(id,EditorFixtures.recipe(id.toString(),EditorFixtures.RAW.replace("0.5","0.9"))); assertEquals(RecipeChangeType.UPDATE,service.snapshot().changes().get(id).type());
        service.reset(id); assertTrue(service.snapshot().changes().isEmpty());
        service.delete(id); assertEquals(RecipeChangeType.DELETE,service.snapshot().changes().get(id).type()); service.reset(id); assertTrue(service.snapshot().changes().isEmpty()); assertEquals(1,service.effective().findById(id).size());
    }
    @Test void snapshotIsImmutableAndPersistedDirtyStateTracksUndo() {
        final var service=EditorFixtures.service(); service.delete(ResourceLocation.parse("test:base")); final var snapshot=service.persistentSnapshot();
        assertThrows(UnsupportedOperationException.class,()->snapshot.changes().clear()); service.markPersisted(snapshot); assertFalse(service.dirty()); service.undo(); assertTrue(service.dirty()); service.redo(); assertFalse(service.dirty());
    }
    @Test void sourceHashConflictQuarantinesChangeAndRetainsSavedRaw() {
        final var service=EditorFixtures.service(); final var id=ResourceLocation.parse("test:base"); service.update(id,EditorFixtures.recipe(id.toString(),EditorFixtures.RAW.replace("0.5","0.9")));
        final var current=new RecipeEditorService(new RecipeIndex(List.of(EditorFixtures.recipe(id.toString(),EditorFixtures.RAW.replace("0.5","0.7"))),new TagIndex(List.of())),EditorFixtures.validator());
        current.restore(service.persistentSnapshot()); assertEquals(1,current.conflicts().size()); assertTrue(current.snapshot().changes().isEmpty());
        assertTrue(current.effective().findById(id).getFirst().rawJson().contains("0.7")); assertTrue(current.persistentSnapshot().changes().get(id).replacement().getFirst().rawJson().contains("0.9"));
        assertFalse(current.dirty()); assertThrows(IllegalStateException.class,()->current.delete(id)); current.discardConflict(id); assertTrue(current.dirty());
    }
}
