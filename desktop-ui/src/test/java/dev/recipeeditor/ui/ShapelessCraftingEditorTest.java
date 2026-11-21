package dev.recipeeditor.ui;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration")
final class ShapelessCraftingEditorTest {
    @Test void addAndRemoveDoNotCreateAShapedPattern()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var forms=new dev.recipeeditor.recipeengine.editor.RecipeFormService();
            final var ingredients=new java.util.ArrayList<>(java.util.List.of("{\"item\":\"minecraft:stone\"}"));
            try(final var textures=new dev.recipeeditor.registry.TextureResourceService()) {
                final var view=new ShapelessCraftingEditorView(ingredients,FxLayoutTestSupport.discovery(),textures,null,index->ingredients.remove(index),()->ingredients.add("{\"item\":\"minecraft:cobblestone\"}"));
                ((RecipeSlotView)view.getChildren().get(1)).fire();assertEquals(2,ingredients.size());
                ((RecipeSlotView)view.getChildren().getFirst()).getContextMenu().getItems().getFirst().fire();assertEquals(1,ingredients.size());
                final String json=forms.shapeless("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[],\"result\":{\"item\":\"minecraft:stone\"}}",ingredients);
                assertFalse(forms.fields(json).containsKey("pattern"));assertEquals("minecraft:crafting_shapeless",forms.type(json));
            }
        });
    }
}
