package dev.recipeeditor.recipeengine.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class VanillaRecipeSchemaTest {
    @Test void selectsOnlyKnownSchemaFieldsAndUsesVersionSpecificDefaults() {
        final var schema = new VanillaRecipeSchema();
        final var campfire = schema.fields("minecraft:campfire_cooking");
        assertEquals("600", campfire.stream().filter(field -> field.key().equals("cookingtime")).findFirst().orElseThrow().defaultJson());
        assertTrue(campfire.stream().anyMatch(field -> field.kind() == VanillaRecipeSchema.Kind.ENUM && field.choices().contains("food")));
        assertTrue(schema.fields("minecraft:crafting_shaped").stream().anyMatch(field -> field.kind() == VanillaRecipeSchema.Kind.BOOLEAN));
        assertFalse(schema.fields("minecraft:crafting_shapeless").stream().anyMatch(field -> field.kind() == VanillaRecipeSchema.Kind.BOOLEAN));
        assertTrue(schema.fields("test:machine").isEmpty());
    }
}
