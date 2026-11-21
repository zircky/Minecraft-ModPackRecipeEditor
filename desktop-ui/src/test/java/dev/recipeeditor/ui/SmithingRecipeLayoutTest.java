package dev.recipeeditor.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SmithingRecipeLayoutTest {
    @Test void smithing_transformHasHonestSlotsAndPreservesJson() {
        final String raw="{\"type\":\"minecraft:smithing_transform\",\"template\":{\"item\":\"minecraft:netherite_upgrade_smithing_template\"},\"base\":{\"item\":\"minecraft:diamond_sword\"},\"addition\":{\"item\":\"minecraft:netherite_ingot\"},\"result\":{\"item\":\"minecraft:netherite_sword\"},\"extension\":{\"keep\":true}}";
        final var recipe=new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.Vanilla1201Parsers().parsers(),new dev.recipeeditor.registry.RegistryIndex(java.util.List.of())).validate("test:technical",raw,new dev.recipeeditor.recipeengine.RecipeSource(dev.recipeeditor.registry.SourceType.LOCAL_RESOURCE,java.util.List.of(),java.nio.file.Path.of("fixture"),"data/test/recipes/technical.json","fixture")).recipe();
        final var model=new RecipeLayoutResolver().resolve(recipe).orElseThrow();
        assertEquals(3,model.inputs().size());assertEquals(false,model.dynamicOutput());assertEquals(raw,recipe.rawJson());
        assertTrue(model.inputs().stream().noneMatch(i->i.role().equals("grid")));
    }
    @Test void smithing_trimHasHonestSlotsAndPreservesJson() {
        final String raw="{\"type\":\"minecraft:smithing_trim\",\"template\":{\"item\":\"minecraft:sentry_armor_trim_smithing_template\"},\"base\":{\"item\":\"minecraft:iron_chestplate\"},\"addition\":{\"item\":\"minecraft:diamond\"},\"extension\":{\"keep\":true}}";
        final var recipe=new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.Vanilla1201Parsers().parsers(),new dev.recipeeditor.registry.RegistryIndex(java.util.List.of())).validate("test:technical",raw,new dev.recipeeditor.recipeengine.RecipeSource(dev.recipeeditor.registry.SourceType.LOCAL_RESOURCE,java.util.List.of(),java.nio.file.Path.of("fixture"),"data/test/recipes/technical.json","fixture")).recipe();
        final var model=new RecipeLayoutResolver().resolve(recipe).orElseThrow();
        assertEquals(3,model.inputs().size());assertEquals(true,model.dynamicOutput());assertEquals(raw,recipe.rawJson());
        assertTrue(model.inputs().stream().noneMatch(i->i.role().equals("grid")));
    }
}
