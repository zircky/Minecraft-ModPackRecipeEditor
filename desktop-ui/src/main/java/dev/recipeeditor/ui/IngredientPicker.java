package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.recipeengine.editor.RecipeFormService;
import dev.recipeeditor.registry.*;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Resource evidence picker; Vanilla inputs have one item per occupied slot, not a made-up count field. */
public final class IngredientPicker extends Dialog<IngredientPicker.Selection> {
    public record Selection(ResourceLocation id,boolean tag,int amount,String json) { }
    public IngredientPicker(final Window owner,final DiscoveryResult result,final ResourceKind kind,final boolean allowTag,final boolean allowAmount) {
        initOwner(owner); getDialogPane().getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("theme.css")).toExternalForm()); setTitle(kind==ResourceKind.FLUID?"Pick fluid reference":"Pick ingredient"); setResizable(true);
        final var search=new TextField(); search.setPromptText("Search name / ID");
        final var mods=new ComboBox<String>(); mods.getItems().add("All mods"); result.registry().entries(kind).stream().flatMap(e->e.sources().stream()).flatMap(s->s.modIds().stream()).distinct().sorted().forEach(mods.getItems()::add); mods.getSelectionModel().selectFirst();
        final var list=new ListView<RegistryEntry>(); list.setPrefHeight(240); list.setCellFactory(v->new ListCell<>() { @Override protected void updateItem(final RegistryEntry entry,final boolean empty) { super.updateItem(entry,empty); setText(empty||entry==null?null:entry.displayName()+" · "+entry.id()); } });
        final var id=new TextField(); id.setPromptText("namespace:path");
        final var knownTags=new ComboBox<String>(); knownTags.setPromptText("Known tags"); knownTags.setDisable(!allowTag);
        result.recipes().tagIndex().definitions().stream().filter(value->value.kind()==kind).map(value->value.id().toString()).distinct().sorted().forEach(knownTags.getItems()::add);
        final var tag=new CheckBox("Tag reference (#)"); tag.setDisable(!allowTag);
        final var amount=new Spinner<Integer>(1,1000000,1); amount.setEditable(true); amount.setDisable(!allowAmount);
        final var warning=new Label("Static evidence only: registration in Minecraft is not confirmed. Missing IDs may still exist in game."); warning.setWrapText(true);
        final var choices=new ArrayList<String>(); final var choiceLabel=new Label();
        final var addChoice=new Button("Add current ID as an alternative"); addChoice.setDisable(!allowTag);
        final var error=new Label(); error.getStyleClass().add("validation-error");
        final var box=new VBox(10,search,mods,list,new Label("Resource Location"),id,tag,new Label(allowAmount?"Amount":"Vanilla input: one item per slot; repeat slots to require more"),amount,knownTags,addChoice,choiceLabel,warning,error); box.setPadding(new Insets(16)); box.setPrefWidth(560); getDialogPane().setContent(box);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        final Runnable filter=()->list.setItems(FXCollections.observableArrayList(new RegistrySearchService(result.registry()).search(kind,search.getText(),mods.getSelectionModel().getSelectedIndex()==0?"":mods.getValue())));
        search.textProperty().addListener((o,a,b)->filter.run()); mods.valueProperty().addListener((o,a,b)->filter.run()); filter.run();
        knownTags.setOnAction(e->{ if(knownTags.getValue()!=null) { id.setText(knownTags.getValue()); tag.setSelected(true); } });
        list.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null) { id.setText(b.id().toString()); tag.setSelected(false); }});
        addChoice.setOnAction(e->{ try { final var location=ResourceLocation.parse(id.getText()); choices.add(new RecipeFormService().ingredient(location,tag.isSelected())); choiceLabel.setText("Alternatives queued: "+choices.size()+". OK adds the current selection too."); error.setText(""); } catch(RuntimeException invalid) {error.setText(invalid.getMessage());} });
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,event->{ try { ResourceLocation.parse(id.getText()); final int value=Integer.parseInt(amount.getEditor().getText()); if(value<1||value>1000000) throw new IllegalArgumentException("Amount must be 1..1000000"); amount.getValueFactory().setValue(value); } catch(RuntimeException invalid) { error.setText(invalid.getMessage()); event.consume(); } });
        setResultConverter(button->{ if(button!=ButtonType.OK) return null; final var location=ResourceLocation.parse(id.getText()); final var forms=new RecipeFormService(); final String current=forms.ingredient(location,tag.isSelected());
            final var all=new ArrayList<>(choices); all.add(current);
            return new Selection(location,tag.isSelected(),amount.getValue(),choices.isEmpty()?current:forms.alternatives(all)); });
    }
}
