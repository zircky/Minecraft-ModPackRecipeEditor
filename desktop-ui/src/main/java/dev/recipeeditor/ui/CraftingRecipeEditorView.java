package dev.recipeeditor.ui;

import java.util.List;
import java.util.function.IntConsumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Grid, output and actions share one layout; selection never edits recipe JSON by itself. */
public final class CraftingRecipeEditorView extends GridPane {
    private int selected=-1;
    public CraftingRecipeEditorView(final GridPane inputs,final List<String> values,final Node output,final int previous,
            final IntConsumer selection,final IntConsumer edit,final IntConsumer clear) {
        getStyleClass().add("crafting-workbench");setHgap(24);setVgap(12);setAlignment(Pos.CENTER);setMaxSize(USE_PREF_SIZE,USE_PREF_SIZE);
        add(new Label("INPUTS"),0,0);add(new Label("RESULT"),2,0);add(inputs,0,1);add(new RecipeArrowView(),1,1);add(output,2,1);
        final var choose=new Button("Choose / Replace Ingredient");final var remove=new Button("Clear Selected Slot");
        choose.setDisable(true);remove.setDisable(true);
        final var group=new ToggleGroup();
        for(int i=0;i<inputs.getChildren().size();i++) {
            final int index=i;final var slot=(RecipeSlotView)inputs.getChildren().get(i);slot.setToggleGroup(group);
            final Runnable select=()->{selected=index;slot.setSelected(true);selection.accept(index);choose.setDisable(false);remove.setDisable(index>=values.size()||values.get(index).isBlank());};
            slot.setOnAction(event->select.run());slot.setOnContextMenuRequested(event->select.run());
            if(index==previous)select.run();
        }
        choose.setOnAction(event->{if(selected>=0)edit.accept(selected);});
        remove.setOnAction(event->{if(selected>=0)clear.accept(selected);});
        final var actions=new FlowPane(choose,remove);actions.getStyleClass().add("filter-row");add(actions,0,2,3,1);
    }
}
