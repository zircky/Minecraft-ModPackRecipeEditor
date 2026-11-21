package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.*;
import java.util.function.BiConsumer;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.ResourceKind;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

public final class RecipeOutputEditor extends VBox {
    public RecipeOutputEditor(final Window owner,final DiscoveryResult result,final RecipeDefinition recipe,final BiConsumer<ResourceLocation,Integer> apply,final Runnable remove) {
        super(8); getChildren().add(new Label("Result"));
        final var textures=new dev.recipeeditor.registry.TextureResourceService();
        sceneProperty().addListener((observable,oldScene,newScene)->{if(oldScene!=null&&newScene==null)textures.close();});
        if(recipe.type().displayId().equals("minecraft:smithing_trim")) { getChildren().add(new Label("Dynamic result: trim modifies the base stack; fixed output editing is unavailable.")); return; }
        final var current=recipe.outputs().stream().filter(o->o instanceof RecipeOutput.Fixed).map(o->(RecipeOutput.Fixed)o).findFirst();
        current.ifPresentOrElse(output->getChildren().add(new HBox(12,new Label("→"),new RecipeSlotView(RecipeSlotContent.parse("{\"item\":\""+output.id()+"\",\"count\":"+output.count()+"}"),result,textures),new Label("× "+output.count()))),()->getChildren().add(new Label("Choose a result item")));
        final var pick=new Button("Choose result / quantity"); pick.setOnAction(e->new IngredientPicker(owner,result,ResourceKind.ITEM,false,true).showAndWait().ifPresent(value->apply.accept(value.id(),value.amount()))); getChildren().add(pick);
        final var removeButton=new Button("Remove result"); removeButton.setDisable(current.isEmpty()); removeButton.setOnAction(e->remove.run()); getChildren().add(removeButton);
        getChildren().add(new Label("This Vanilla type supports one fixed output. Removing it in Raw JSON blocks Save until repaired."));
    }
}
