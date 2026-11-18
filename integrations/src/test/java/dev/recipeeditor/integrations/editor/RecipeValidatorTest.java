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
final class RecipeValidatorTest {
    private RecipeValidationResult validate(final String raw) { return EditorFixtures.validator().validate("test:recipe",raw,EditorFixtures.recipe("test:base",EditorFixtures.RAW).source()); }
    @Test void validRecipeWithUnknownResourcesWarnsWithoutBlocking() {
        final var value=validate(EditorFixtures.RAW); assertTrue(value.valid()); assertTrue(value.issues().stream().anyMatch(i->i.severity()==ValidationIssue.Severity.WARNING));
    }
    @Test void invalidIdResultCountAndNumericParameters() {
        assertThrows(IllegalArgumentException.class,()->EditorFixtures.validator().validate("Bad:ID",EditorFixtures.RAW,EditorFixtures.recipe("test:base",EditorFixtures.RAW).source()));
        assertFalse(validate("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"test:a\"}}").valid());
        assertFalse(validate(EditorFixtures.RAW.replace("\"test:ingot\"","{\"item\":\"test:ingot\",\"count\":0}")).valid());
        assertFalse(validate(EditorFixtures.RAW.replace("0.5","-1")).valid()); assertFalse(validate("not JSON").valid());
    }
    @Test void unknownTypesHaveOnlyLimitedValidation() { final var result=validate("{\"type\":\"custom:unknown\",\"arbitrary\":42}"); assertTrue(result.valid()); assertEquals(RecipeParseStatus.UNSUPPORTED,result.recipe().status()); assertFalse(result.issues().isEmpty()); }
    @Test void hugeEditedDataIsRejected() { assertThrows(IllegalArgumentException.class,()->validate("x".repeat(2*1024*1024+1))); }
}
