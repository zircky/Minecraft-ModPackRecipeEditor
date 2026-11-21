package dev.recipeeditor.ui;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") final class RecipeViewerIntegrationTest {
    @Test void viewerCannotChangeARecipe()throws Exception {
        final var recipe=RecipeLayoutTestFixtures.cooking();final String original=recipe.rawJson();
        FxLayoutTestSupport.run(()->{try(final var textures=new dev.recipeeditor.registry.TextureResourceService()) {
            final var view=new RecipeLayoutRenderer(new RecipeLayoutResolver().resolve(recipe).orElseThrow(),FxLayoutTestSupport.discovery(),textures,64,"en_us",RecipeLayoutRenderer.Mode.VIEWER,null);
            new javafx.scene.Scene(view,800,500);view.applyCss();view.layout();
            for(final var node:view.lookupAll(".minecraft-recipe-slot")){final var slot=(RecipeSlotView)node;slot.fire();assertNull(slot.getContextMenu());assertFalse(slot.isSelected());}
            assertEquals(original,recipe.rawJson());assertTrue(view.lookupAll(".text-input").isEmpty());
        }});
    }
}
