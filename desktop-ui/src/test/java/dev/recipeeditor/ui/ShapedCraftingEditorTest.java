package dev.recipeeditor.ui;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration")
final class ShapedCraftingEditorTest {
    @Test void readingPreservesPatternAndClearingPatchesOnlyOnExplicitAction()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var forms=new dev.recipeeditor.recipeengine.editor.RecipeFormService();
            final String original="{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"AB\"],\"key\":{\"A\":{\"item\":\"minecraft:stone\"},\"B\":{\"item\":\"minecraft:cobblestone\"}},\"result\":{\"item\":\"minecraft:stone\"},\"custom\":true}";
            final var draft=new java.util.concurrent.atomic.AtomicReference<>(original);final var slots=new java.util.ArrayList<>(forms.grid(original));
            try(final var textures=new dev.recipeeditor.registry.TextureResourceService()) {
                final var view=new ShapedCraftingEditorView(slots,FxLayoutTestSupport.discovery(),textures,null,index->{slots.set(index,"");draft.set(forms.shaped(draft.get(),slots));});
                assertEquals(9,view.getChildren().size());assertEquals(original,draft.get());
                final var first=(RecipeSlotView)view.getChildren().getFirst();first.getContextMenu().getItems().getFirst().fire();
                assertNotEquals(original,draft.get());assertEquals("true",forms.fields(draft.get()).get("custom"));assertTrue(draft.get().contains("cobblestone"));
                assertEquals(RecipeSlotState.EMPTY,((RecipeSlotView)view.getChildren().getLast()).slotState());
            }
        });
    }
}
