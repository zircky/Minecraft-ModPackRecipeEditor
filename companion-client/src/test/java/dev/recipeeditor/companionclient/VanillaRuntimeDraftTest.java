package dev.recipeeditor.companionclient;

import com.google.gson.JsonParser;
import dev.recipeeditor.companionprotocol.RuntimeData.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class VanillaRuntimeDraftTest {
    @Test void makesAnIndependentValidatedCreateChangeWithUndoRedo() {
        final var recipe = RecipeTypeRegistryTest.cooking();
        final var mapper = new VanillaRuntimeDraft();
        final var payload = mapper.copy(recipe);
        final var json = JsonParser.parseString(payload).getAsJsonObject();
        assertEquals("minecraft:iron_ingot", json.get("result").getAsString());
        assertEquals(250, json.get("cookingtime").getAsInt());
        assertEquals("forge:ore", json.getAsJsonObject("ingredient").get("tag").getAsString());
        assertTrue(recipe.properties().has("cookingTime"));
        final var validator = new RecipeValidator(new Vanilla1201Parsers().parsers(), new RegistryIndex(List.of()));
        final var editor = new RecipeEditorService(new RecipeIndex(List.of(), new TagIndex(List.of())), validator);
        final var id = ResourceLocation.parse("test:copied");
        final var source = new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),Path.of("project.json"),"changes/test:copied","test");
        final var result = validator.validate(id, payload, source);
        assertTrue(result.valid(), result.issues().toString());
        editor.create(result.recipe());
        assertEquals(RecipeChangeType.CREATE, editor.snapshot().changes().get(id).type());
        editor.undo();
        assertTrue(editor.snapshot().changes().isEmpty());
        editor.redo();
        assertEquals(1, editor.effective().findById(id).size());
    }

    @Test void rejectsMissingSemanticsAndNbtInsteadOfDroppingThem() {
        final var recipe = RecipeTypeRegistryTest.cooking();
        final var properties = recipe.properties();
        properties.getAsJsonObject("result").addProperty("nbtSnbt", "{test:1b}");
        final var withNbt = new Recipe(recipe.id(), recipe.type(), recipe.serializer(), recipe.runtimeClass(),
                recipe.adapter(), recipe.status(), properties, recipe.sourceInformation(), List.of());
        final var mapper = new VanillaRuntimeDraft();
        assertFalse(mapper.supports(withNbt));
        assertThrows(IllegalArgumentException.class, () -> mapper.copy(withNbt));
        properties.remove("ingredients");
        final var incomplete = new Recipe(recipe.id(), recipe.type(), recipe.serializer(), recipe.runtimeClass(),
                recipe.adapter(), recipe.status(), properties, recipe.sourceInformation(), List.of());
        assertFalse(mapper.supports(incomplete));
    }
}
