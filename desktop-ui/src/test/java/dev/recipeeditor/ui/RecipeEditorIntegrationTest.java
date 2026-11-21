package dev.recipeeditor.ui;
import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") final class RecipeEditorIntegrationTest {
    @Test void directInputAndOutputActionsAlsoWorkForCreator()throws Exception {
        FxLayoutTestSupport.run(()->{try(final var textures=new dev.recipeeditor.registry.TextureResourceService()) {
            for(final var mode:java.util.List.of(RecipeLayoutRenderer.Mode.EDITOR,RecipeLayoutRenderer.Mode.CREATOR)) {
                final var edits=new AtomicInteger();final var clears=new AtomicInteger();final var outputs=new AtomicInteger();
                final var actions=new RecipeLayoutRenderer.Actions((role,index)->{assertEquals("ingredient",role);edits.incrementAndGet();},(role,index)->clears.incrementAndGet(),outputs::incrementAndGet);
                final var view=new RecipeLayoutRenderer(new RecipeLayoutResolver().resolve(RecipeLayoutTestFixtures.cooking()).orElseThrow(),FxLayoutTestSupport.discovery(),textures,64,"en_us",mode,actions);
                new javafx.scene.Scene(view,800,500);view.applyCss();view.layout();
                final var input=(RecipeSlotView)view.lookup(".recipe-input-slot");input.fire();input.getContextMenu().getItems().getFirst().fire();((RecipeSlotView)view.lookup(".recipe-output-slot")).fire();
                assertEquals(1,edits.get());assertEquals(1,clears.get());assertEquals(1,outputs.get());
            }
        }});
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"crafting_shaped","crafting_shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","smithing_transform","smithing_trim"})
    void emptyCreatorTemplatesHaveLayoutsWithoutInventedResults(final String type) {
        final var forms=new dev.recipeeditor.recipeengine.editor.RecipeFormService();
        final var source=RecipeLayoutTestFixtures.cooking().source();
        final var validator=new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.Vanilla1201Parsers().parsers(),new dev.recipeeditor.registry.RegistryIndex(java.util.List.of()));
        final var recipe=validator.validate("test:empty",forms.template("minecraft:"+type),source).recipe();
        final var model=new RecipeLayoutResolver().resolve(recipe).orElseThrow();
        assertNull(model.output().id());assertTrue(model.inputs().stream().allMatch(input->input.json().isBlank()));
        assertFalse(validator.validate("test:empty",recipe.rawJson(),source).valid());
    }

}
