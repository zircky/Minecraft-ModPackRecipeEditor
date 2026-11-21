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
        if(model.machine().isPresent()) {
            if(editable)throw new IllegalArgumentException("Machine layout is read only until an authoring adapter is verified");
            getChildren().add(new ProcessingRecipeLayoutView(model.machine().get(),discovery,textures,size,language));return;
        }
        if(!model.parameters().isEmpty()){final var properties=new FlowPane(16,8);properties.setAlignment(Pos.CENTER);model.parameters().forEach((name,value)->properties.getChildren().add(new Label(name+": "+value)));getChildren().add(properties);}
        final var row=new GridPane();row.setHgap(16);row.setVgap(8);row.setAlignment(Pos.CENTER);row.setMaxSize(USE_PREF_SIZE,USE_PREF_SIZE);
        row.getStyleClass().add("recipe-layout-slots");
        final Node input;
        if(model.kind()==RecipeLayoutProvider.Kind.SHAPED||model.kind()==RecipeLayoutProvider.Kind.SHAPELESS) {
            final var values=model.inputs().stream().map(RecipeLayoutModel.Input::json).toList();
            if(model.kind()==RecipeLayoutProvider.Kind.SHAPED)input=new ShapedCraftingEditorView(values,discovery,textures,size,language,editable?i->actions.chooseInput().accept("grid",i):null,editable?i->actions.clearInput().accept("grid",i):null);
            else {final var occupied=values.stream().takeWhile(value->!value.isBlank()).toList();input=new ShapelessCraftingEditorView(occupied,discovery,textures,size,language,editable?i->actions.chooseInput().accept("shapeless",i):null,editable?i->actions.clearInput().accept("shapeless",i):null,editable?()->actions.chooseInput().accept("shapeless",occupied.size()):null);}
            row.add(new Label("INPUTS"),0,0);
        } else {
            int column=0;
            for(final var value:model.inputs()) {
                if(column>0){row.add(new Label("+"),column++,1);}
                final var label=new Label(value.role().toUpperCase(Locale.ROOT));row.add(label,column,0);GridPane.setHalignment(label,HPos.CENTER);
                final var slot=slot(RecipeSlotContent.parse(value.json()),discovery,textures,size,language);
                slot.getStyleClass().add("recipe-input-slot");
                if(editable)slot.editable(()->actions.chooseInput().accept(value.role(),value.index()),()->actions.clearInput().accept(value.role(),value.index()));
                else slot.setOnAction(event->slot.setSelected(false));
                row.add(slot,column++,1);
            }
            input=null;
            final int arrowColumn=column;
            row.add(new RecipeArrowView(),arrowColumn,1);row.add(new Label("RESULT"),arrowColumn+1,0);
            row.add(output(model,discovery,textures,size,language,editable,actions),arrowColumn+1,1);finish(row,editable);return;
        }
        row.add(input,0,1);row.add(new RecipeArrowView(),1,1);row.add(new Label("RESULT"),2,0);row.add(output(model,discovery,textures,size,language,editable,actions),2,1);
        for(final var child:row.getChildren())GridPane.setHalignment(child,HPos.CENTER);
        finish(row,editable);
    }
    private void finish(final GridPane row,final boolean editable) {
        for(final var child:row.getChildren())GridPane.setHalignment(child,HPos.CENTER);
        if(!editable)visitSlots(row,slot->slot.setOnAction(event->slot.setSelected(false)));
        getChildren().add(row);
    }
    /** Navigation never edits ingredient JSON; tags open only an explicitly indexed preview member. */
    public void resourceNavigation(final BiConsumer<dev.recipeeditor.registry.ResourceKind,dev.recipeeditor.core.ResourceLocation> navigate) {
        visitSlots(this,slot->{
            if(slot.getContextMenu()!=null)return;
            if(!(slot.getUserData() instanceof RecipeSlotContent content))return;
            if(content.kind()==RecipeSlotContent.Kind.ALTERNATIVES&&!content.alternatives().isEmpty())content=content.alternatives().getFirst();
            if(content.id()==null||content.kind()==RecipeSlotContent.Kind.TAG||content.kind()==RecipeSlotContent.Kind.CHEMICAL)return;
            final var target=content;slot.setOnAction(event->{slot.setSelected(false);navigate.accept(target.kind()==RecipeSlotContent.Kind.FLUID?dev.recipeeditor.registry.ResourceKind.FLUID:target.kind()==RecipeSlotContent.Kind.BLOCK?dev.recipeeditor.registry.ResourceKind.BLOCK:dev.recipeeditor.registry.ResourceKind.ITEM,target.id());});
        });
    }
    private void visitSlots(final javafx.scene.Parent parent,final java.util.function.Consumer<RecipeSlotView> action) {
        for(final var node:parent.getChildrenUnmodifiable())if(node instanceof RecipeSlotView slot)action.accept(slot);else if(node instanceof javafx.scene.Parent nested)visitSlots(nested,action);
    }
    private RecipeSlotView slot(final RecipeSlotContent value,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final String language){return new RecipeSlotView(value,discovery,textures,size,language);}
    private Node output(final RecipeLayoutModel model,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final String language,final boolean editable,final Actions actions) {
        if(model.dynamicOutput()){final var label=new Label("Dynamic trim result\nDepends on base stack and trim inputs");label.setWrapText(true);label.setMaxWidth(180);return label;}
        final var slot=slot(model.output(),discovery,textures,size,language);slot.getStyleClass().add("recipe-output-slot");
        if(editable){slot.setOnAction(event->{slot.setSelected(false);actions.chooseOutput().run();});final var choose=new MenuItem("Choose result / quantity");choose.setOnAction(event->actions.chooseOutput().run());slot.setContextMenu(new ContextMenu(choose));}
        else slot.setOnAction(event->slot.setSelected(false));
        return slot;
    }
}
