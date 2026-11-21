package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.TextureResourceService;
import java.util.*;
import java.util.function.BiConsumer;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Viewer, editor and creator share slot rows. Labels/parameters never affect a slot's alignment. */
public final class RecipeLayoutRenderer extends VBox {
    public enum Mode { VIEWER, EDITOR, CREATOR }
    public record Actions(BiConsumer<String,Integer> chooseInput,BiConsumer<String,Integer> clearInput,Runnable chooseOutput) {
        public Actions{Objects.requireNonNull(chooseInput);Objects.requireNonNull(clearInput);Objects.requireNonNull(chooseOutput);}
    }
    public RecipeLayoutRenderer(final RecipeLayoutModel model,final DiscoveryResult discovery,final TextureResourceService textures,
            final double size,final String language,final Mode mode,final Actions actions) {
        if(size<40||size>80)throw new IllegalArgumentException("Recipe slot size must be 40..80");
        final boolean editable=mode!=Mode.VIEWER;if(editable)Objects.requireNonNull(actions);
        getStyleClass().add("recipe-layout");setSpacing(12);setAlignment(Pos.TOP_CENTER);
        final var title=new Label(new RecipeTypeDisplayNameResolver().machine(model.recipeType(),language));title.getStyleClass().add("section-title");getChildren().add(title);
        if(!model.parameters().isEmpty()){final var properties=new FlowPane(16,8);properties.setAlignment(Pos.CENTER);model.parameters().forEach((name,value)->properties.getChildren().add(new Label(name+": "+value)));getChildren().add(properties);}
        final var row=new GridPane();row.setHgap(16);row.setVgap(8);row.setAlignment(Pos.CENTER);row.setMaxSize(USE_PREF_SIZE,USE_PREF_SIZE);
        row.getStyleClass().add("recipe-layout-slots");
        final Node input;
        if(model.kind()==RecipeLayoutProvider.Kind.SHAPED||model.kind()==RecipeLayoutProvider.Kind.SHAPELESS) {
            final var values=model.inputs().stream().map(RecipeLayoutModel.Input::json).toList();
            if(model.kind()==RecipeLayoutProvider.Kind.SHAPED)input=new ShapedCraftingEditorView(values,discovery,textures,size,editable?i->actions.chooseInput().accept("grid",i):null,editable?i->actions.clearInput().accept("grid",i):null);
            else {final var occupied=values.stream().takeWhile(value->!value.isBlank()).toList();input=new ShapelessCraftingEditorView(occupied,discovery,textures,size,editable?i->actions.chooseInput().accept("shapeless",i):null,editable?i->actions.clearInput().accept("shapeless",i):null,editable?()->actions.chooseInput().accept("shapeless",occupied.size()):null);}
            row.add(new Label("INPUTS"),0,0);
        } else {
            final var roles=new GridPane();roles.setHgap(12);roles.setVgap(8);roles.setAlignment(Pos.CENTER);
            int column=0;
            for(final var value:model.inputs()) {
                if(column>0){roles.add(new Label("+"),column++,1);}
                final var label=new Label(value.role().toUpperCase(Locale.ROOT));roles.add(label,column,0);GridPane.setHalignment(label,HPos.CENTER);
                final var slot=slot(RecipeSlotContent.parse(value.json()),discovery,textures,size);
                slot.getStyleClass().add("recipe-input-slot");
                if(editable)slot.editable(()->actions.chooseInput().accept(value.role(),value.index()),()->actions.clearInput().accept(value.role(),value.index()));
                else slot.setOnAction(event->slot.setSelected(false));
                roles.add(slot,column++,1);
            }
            // Roles share their label row with the output label, keeping slot centers on the same axis.
            for(final Node child:List.copyOf(roles.getChildren())) {
                final int col=Optional.ofNullable(GridPane.getColumnIndex(child)).orElse(0),r=Optional.ofNullable(GridPane.getRowIndex(child)).orElse(0);
                roles.getChildren().remove(child);row.add(child,col,r);GridPane.setHalignment(child,HPos.CENTER);
            }
            input=null;
            final int arrowColumn=column;
            row.add(new RecipeArrowView(),arrowColumn,1);row.add(new Label("RESULT"),arrowColumn+1,0);
            row.add(output(model,discovery,textures,size,editable,actions),arrowColumn+1,1);getChildren().add(row);return;
        }
        row.add(input,0,1);row.add(new RecipeArrowView(),1,1);row.add(new Label("RESULT"),2,0);row.add(output(model,discovery,textures,size,editable,actions),2,1);
        for(final var child:row.getChildren())GridPane.setHalignment(child,HPos.CENTER);
        getChildren().add(row);
    }
    private RecipeSlotView slot(final RecipeSlotContent value,final DiscoveryResult discovery,final TextureResourceService textures,final double size){return new RecipeSlotView(value,discovery,textures,size);}
    private Node output(final RecipeLayoutModel model,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final boolean editable,final Actions actions) {
        if(model.dynamicOutput()){final var label=new Label("Dynamic trim result\nDepends on base stack and trim inputs");label.setWrapText(true);label.setMaxWidth(180);return label;}
        final var slot=slot(model.output(),discovery,textures,size);slot.getStyleClass().add("recipe-output-slot");
        if(editable){slot.setOnAction(event->{slot.setSelected(false);actions.chooseOutput().run();});final var choose=new MenuItem("Choose result / quantity");choose.setOnAction(event->actions.chooseOutput().run());slot.setContextMenu(new ContextMenu(choose));}
        else slot.setOnAction(event->slot.setSelected(false));
        return slot;
    }
}
