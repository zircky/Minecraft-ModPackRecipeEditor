package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.RecipeSource;
import dev.recipeeditor.registry.SourceType;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeEditSessionTest {
    @Test void draftSaveCancelAndModeDoNotLoseRawText() {
        final var session=new RecipeEditSession(ResourceLocation.parse("test:a"),new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),Path.of("draft"),"","draft"),"{}");
        session.setRawJson("invalid JSON"); session.mode(RecipeEditSession.Mode.RAW_JSON); session.mode(RecipeEditSession.Mode.VISUAL);
        assertTrue(session.dirty()); assertEquals("invalid JSON",session.rawJson()); session.cancel(); assertEquals("{}",session.rawJson()); assertFalse(session.dirty());
        session.setRawJson("{\"type\":\"custom:a\"}"); session.markSaved(session.rawJson()); assertFalse(session.dirty()); assertEquals("{}",session.originalJson());
    }
}
