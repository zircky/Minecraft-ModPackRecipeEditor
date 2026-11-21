package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.RecipeMachineLayout.*;
import dev.recipeeditor.registry.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Variable machine sections reuse slot textures/tooltips without pretending to be crafting grids. */
final class ProcessingRecipeLayoutView extends VBox {
    ProcessingRecipeLayoutView(final RecipeMachineLayout model,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final String language) {
        setSpacing(14);setAlignment(Pos.TOP_CENTER);getStyleClass().add("processing-recipe-layout");
        final var inputs=new VBox(12);inputs.setAlignment(Pos.CENTER);
        final var outputs=new VBox(12);outputs.setAlignment(Pos.CENTER);
        for(final var group:model.groups()) {
            final Node section=section(group,discovery,textures,size,language);
            if(group.direction()==Direction.OUTPUT)outputs.getChildren().add(section);else if(group.direction()==Direction.INPUT)inputs.getChildren().add(section);
        }
        model.brew().ifPresent(brew->{
            final var title=new Label((language.equals("ru_ru")?"Напиток: ":"Brew: ")+brew.displayName(language));title.getStyleClass().add("section-title");
            final var evidence=new Label("Brew ID: "+brew.id()+"\nDeclared registry reference; effects, mana cost and container require runtime evidence");evidence.setWrapText(true);evidence.setMaxWidth(340);
            outputs.getChildren().add(new VBox(8,new Label("BREW RESULT"),title,evidence));
        });
        if(model.style()==Style.BREWING) {final var arrow=new RecipeArrowView();arrow.setRotate(90);getChildren().addAll(inputs,arrow,outputs);}
        else {final var row=new HBox(18,inputs,new RecipeArrowView(),outputs);row.setAlignment(Pos.CENTER);getChildren().add(row);}
        for(final var group:model.groups())if(group.direction()==Direction.AUXILIARY)getChildren().add(section(group,discovery,textures,size,language));
        model.properties().forEach((name,value)->{final var text=new Label(name+": "+(value.length()>240?value.substring(0,240)+"… (full value in Raw JSON)":value));text.setWrapText(true);text.setMaxWidth(600);getChildren().add(text);});
    }
    private Node section(final Group group,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final String language) {
        final var slots=new FlowPane(6,6);slots.setAlignment(Pos.CENTER);slots.setPrefWrapLength((size+6)*4);slots.setMaxWidth((size+6)*4);
        for(final var value:group.slots()) {
            RecipeSlotContent content=RecipeSlotContent.parse(value.rawJson());
            if(value.kind()==SlotKind.BLOCK&&content.kind()==RecipeSlotContent.Kind.TAG)content=new RecipeSlotContent(RecipeSlotContent.Kind.UNKNOWN,null,1,java.util.List.of(),RecipeSlotState.UNKNOWN);
            final var slot=new RecipeSlotView(content,discovery,textures,size,language);slot.setOnAction(event->slot.setSelected(false));
            slot.getStyleClass().add(group.direction()==Direction.OUTPUT?"recipe-output-slot":"recipe-input-slot");
            slot.setAccessibleText(value.role()+" · "+value.kind());
            if(value.kind()==SlotKind.BLOCK)slot.setTooltip(new Tooltip("Block-state catalyst\n"+value.rawJson()+"\n"+slot.getTooltip().getText()));
            slots.getChildren().add(slot);
        }
        final var box=new VBox(8,new Label(group.label()),slots);box.setAlignment(Pos.CENTER);return box;
    }
}
