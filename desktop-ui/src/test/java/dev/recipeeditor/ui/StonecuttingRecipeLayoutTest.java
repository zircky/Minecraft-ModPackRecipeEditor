package dev.recipeeditor.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class StonecuttingRecipeLayoutTest {
    @Test void stonecuttingHasHonestSlotsAndPreservesJson() {
        final String raw="{\"type\":\"minecraft:stonecutting\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:stone_slab\",\"count\":2,\"extension\":{\"keep\":true}}";
        final var recipe=new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.Vanilla1201Parsers().parsers(),new dev.recipeeditor.registry.RegistryIndex(java.util.List.of())).validate("test:technical",raw,new dev.recipeeditor.recipeengine.RecipeSource(dev.recipeeditor.registry.SourceType.LOCAL_RESOURCE,java.util.List.of(),java.nio.file.Path.of("fixture"),"data/test/recipes/technical.json","fixture")).recipe();
        final var model=new RecipeLayoutResolver().resolve(recipe).orElseThrow();
        assertEquals(1,model.inputs().size());assertEquals(false,model.dynamicOutput());assertEquals(raw,recipe.rawJson());
        assertTrue(model.inputs().stream().noneMatch(i->i.role().equals("grid")));
    }
}
