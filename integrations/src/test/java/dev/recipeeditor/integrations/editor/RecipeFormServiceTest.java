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
final class RecipeFormServiceTest {
    @Test void shapedTrimsEmptyBordersAndPreservesUnknownFields() {
        final var forms=new RecipeFormService(); final var grid=new ArrayList<>(Collections.nCopies(9,"")); grid.set(4,forms.ingredient(ResourceLocation.parse("test:a"),false)); grid.set(8,forms.ingredient(ResourceLocation.parse("forge:ingots"),true));
        final String raw=forms.shaped("{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"item\":\"test:r\"},\"custom\":{\"keep\":true}}",grid);
        final var result=EditorFixtures.validator().validate("test:a",raw,EditorFixtures.recipe("test:a",EditorFixtures.RAW).source()); assertTrue(result.valid()); assertEquals(List.of("A "," B"),result.recipe().layout().pattern()); assertTrue(raw.contains("keep"));
        assertFalse(EditorFixtures.validator().validate("test:a",forms.shaped(raw,Collections.nCopies(9,"")),EditorFixtures.recipe("test:a",EditorFixtures.RAW).source()).valid());
    }
    @Test void nestedOutputFieldsAndCustomPropertiesSurviveVisualPatches() {
        final var forms=new RecipeFormService(); final String original=EditorFixtures.RAW.replace("\"test:ingot\"","{\"item\":\"test:ingot\",\"count\":1,\"nbt\":\"{foo:1}\"}");
        final String changed=forms.result(original,ResourceLocation.parse("test:new"),3); assertTrue(changed.contains("foo:1")); assertTrue(changed.contains("test:new"));
        assertTrue(forms.patch(changed,"cookingtime","100").contains("foo:1"));
    }
    @Test void invalidRawCannotExposeStaleVisualState() {
        final var service=new RecipeDraftPreviewService(); final var preview=service.inspect(EditorFixtures.validator(),ResourceLocation.parse("test:a"),EditorFixtures.recipe("test:a",EditorFixtures.RAW).source(),EditorFixtures.RAW,"broken JSON");
        assertFalse(preview.visualSupported()); assertFalse(preview.validation().valid()); assertTrue(preview.fields().isEmpty()); assertFalse(preview.diffError().isEmpty());
    }
}
