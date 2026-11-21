package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.util.function.BiConsumer;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Output selection reuses the editor's assets/cache; mandatory results cannot be cleared accidentally. */
public final class RecipeOutputEditor extends VBox {
    public RecipeOutputEditor(final Window owner,final DiscoveryResult result,final RecipeDefinition recipe,
            final TextureResourceService textures,final double size,final BiConsumer<ResourceLocation,Integer> apply) {
        super(8);setAlignment(javafx.geometry.Pos.CENTER);
        if(recipe.type().displayId().equals("minecraft:smithing_trim")) {
            final var dynamic=new Label("Dynamic result: trim modifies the base stack.");dynamic.setWrapText(true);getChildren().add(dynamic);return;
        }
        final var current=recipe.outputs().stream().filter(RecipeOutput.Fixed.class::isInstance).map(RecipeOutput.Fixed.class::cast).findFirst();
        final var content=current.map(output->RecipeSlotContent.parse("{\"item\":\""+output.id()+"\",\"count\":"+output.count()+"}")).orElseGet(()->RecipeSlotContent.parse(""));
        final var slot=new RecipeSlotView(content,result,textures,size);slot.getStyleClass().add("recipe-output-slot");slot.setAccessibleText("Choose result and quantity");
        final Runnable pick=()->new IngredientPicker(owner,result,ResourceKind.ITEM,false,true).showAndWait().ifPresent(value->apply.accept(value.id(),value.amount()));
        slot.setOnAction(event->pick.run());
        final var replace=new MenuItem("Choose result / quantity");replace.setOnAction(event->pick.run());
        final var required=new MenuItem("Result is required for this Vanilla recipe");required.setDisable(true);
        slot.setContextMenu(new ContextMenu(replace,required));getChildren().add(slot);
    }
}
