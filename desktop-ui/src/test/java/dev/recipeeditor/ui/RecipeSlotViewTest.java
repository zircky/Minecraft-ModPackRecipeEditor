package dev.recipeeditor.ui;
import dev.recipeeditor.core.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeSlotViewTest {
    @org.junit.jupiter.api.Tag("integration") @Test void javafxSlotRendersQuantityAndHasExplicitSelectionAndDisabledStates()throws Exception {
        FxLayoutTestSupport.run(()->{
            try(final var textures=new dev.recipeeditor.registry.TextureResourceService()) {
                final var slot=new RecipeSlotView(RecipeSlotContent.parse("{\"item\":\"minecraft:stone\",\"count\":64}"),FxLayoutTestSupport.discovery(),textures);
                assertEquals(36,slot.getPrefWidth());assertTrue(slot.getTooltip().getText().contains("minecraft:stone"));
                final var graphic=(javafx.scene.layout.StackPane)slot.getGraphic();assertTrue(graphic.getChildren().stream().anyMatch(node->node instanceof javafx.scene.control.Label label&&label.getText().equals("64")));
                slot.setSelected(true);assertEquals(RecipeSlotState.SELECTED,slot.slotState());slot.setDisable(true);assertEquals(RecipeSlotState.DISABLED,slot.slotState());
                final var empty=new RecipeSlotView(RecipeSlotContent.parse(""),FxLayoutTestSupport.discovery(),textures);assertTrue(((javafx.scene.layout.StackPane)empty.getGraphic()).getChildren().isEmpty());
            }
        });
    }
    @Test void retainsItemQuantityAndDistinguishesChemicalFromFluid() {
        final var item=RecipeSlotContent.parse("{\"item\":\"minecraft:stone\",\"count\":64}");
        assertEquals(ResourceLocation.parse("minecraft:stone"),item.id());assertEquals(64,item.amount());
        assertEquals(RecipeSlotContent.Kind.FLUID,RecipeSlotContent.parse("{\"fluid\":\"minecraft:water\",\"amount\":1000}").kind());
        assertEquals(RecipeSlotContent.Kind.CHEMICAL,RecipeSlotContent.parse("{\"chemical\":\"mekanism:oxygen\",\"amount\":5}").kind());
    }
    @Test void emptyTagsAlternativesAndInvalidAreExplicit() {
        assertEquals(RecipeSlotState.EMPTY,RecipeSlotContent.parse("").state());
        assertEquals(RecipeSlotContent.Kind.TAG,RecipeSlotContent.parse("{\"tag\":\"minecraft:logs\"}").kind());
        assertEquals(2,RecipeSlotContent.parse("[{\"item\":\"minecraft:oak_log\"},{\"item\":\"minecraft:birch_log\"}]").alternatives().size());
        assertEquals(RecipeSlotState.INVALID,RecipeSlotContent.parse("{\"item\":\"minecraft:stone\",\"count\":0}").state());
        assertEquals(RecipeSlotState.INVALID,RecipeSlotContent.parse("{").state());
        assertEquals(RecipeSlotState.UNKNOWN,RecipeSlotContent.parse("{\"custom\":true}").state());
    }
}
