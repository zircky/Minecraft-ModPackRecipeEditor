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
        this(owner,result,kind,allowTag,allowAmount,"");
    }
    public IngredientPicker(final Window owner,final DiscoveryResult result,final ResourceKind kind,final boolean allowTag,final boolean allowAmount,final String initialJson) {
        initOwner(owner); new UiTheme().apply(getDialogPane()); setTitle(kind==ResourceKind.FLUID?"Pick fluid reference":"Pick ingredient"); setResizable(true);
        final var search=new TextField(); search.setPromptText("Search name / ID");
        final var mods=new ComboBox<String>(); mods.getItems().add("All mods"); result.registry().entries(kind).stream().flatMap(e->e.sources().stream()).flatMap(s->s.modIds().stream()).distinct().sorted().forEach(mods.getItems()::add); mods.getSelectionModel().selectFirst();
        final var textures=new TextureResourceService();
        addEventHandler(javafx.scene.control.DialogEvent.DIALOG_HIDDEN,event->textures.close());
        final var entries=new ArrayList<RegistryEntry>();
        final var list=new PaginationView();list.setPrefHeight(420);
        final var id=new TextField(); id.setPromptText("namespace:path");
        final var knownTags=new ComboBox<String>(); knownTags.setPromptText("Known tags"); knownTags.setDisable(!allowTag);
        result.recipes().tagIndex().definitions().stream().filter(value->value.kind()==kind).map(value->value.id().toString()).distinct().sorted().forEach(knownTags.getItems()::add);
        final var mode=new ComboBox<String>();mode.getItems().add("Exact Item");if(allowTag)mode.getItems().add("Tag");mode.getSelectionModel().selectFirst();
        final var tag=new CheckBox();tag.selectedProperty().bind(mode.valueProperty().isEqualTo("Tag"));
        final var variants=new FlowPane(6,6);variants.setPrefWrapLength(500);
        final var suggestions=new ComboBox<String>();suggestions.setPromptText("Tags containing this item");suggestions.setDisable(!allowTag);
        final var amount=new Spinner<Integer>(1,1000000,1); amount.setEditable(true); amount.setDisable(!allowAmount);
        final var warning=new Label("Registry confidence is shown for each resource. Missing IDs may still exist in game. Tags remain references, not a single item."); warning.setWrapText(true);
        final var selection=new IngredientSelectionModel(initialJson);
        final var choices=new ListView<String>(FXCollections.observableArrayList(selection.choices()));choices.setPrefHeight(85);
        final boolean[] changed={false};final boolean[] loading={false};
        final var applyChoice=new Button("Replace selected alternative");applyChoice.setDisable(!allowTag);
        final var removeChoice=new Button("Remove selected alternative");removeChoice.setDisable(!allowTag);
        final var addChoice=new Button("Add current ID as an alternative"); addChoice.setDisable(!allowTag);
        final var error=new Label(); error.getStyleClass().add("validation-error");
        final var box=new VBox(10,search,mods,list,new Label("Ingredient mode"),mode,new Label("Resource Location"),id,new Label(allowAmount?"Amount":"Vanilla input: one item per slot; repeat slots to require more"),amount,knownTags,suggestions,variants,choices,new HBox(8,applyChoice,addChoice,removeChoice),warning,error); box.setPadding(new Insets(16)); box.setPrefWidth(560); final var contentScroll=new ScrollPane(box);contentScroll.setFitToWidth(true);contentScroll.setPrefViewportHeight(690);getDialogPane().setContent(contentScroll);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        final javafx.util.Callback<Integer,javafx.scene.Node> page=number->{
            final var grid=new TilePane(8,8);grid.setPrefColumns(3);
            for(int index=number*30;index<Math.min(entries.size(),number*30+30);index++) {
                final var entry=entries.get(index);final var button=new Button();button.setPrefSize(150,115);
                final String language=Locale.getDefault().getLanguage().equals("ru")?"ru_ru":"en_us";
                final var name=new Label(new ResourceBrowserViewModel(result.registry()).displayName(entry,language));name.setMaxWidth(125);name.setTextOverrun(OverrunStyle.ELLIPSIS);
                button.setGraphic(new VBox(5,new ResourcePreview(entry.visual(),result.selectedRoot(),textures,40,()->button.getScene()!=null,preview->{}),name,new Label(entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME?"Runtime confirmed":"Static candidate")));
                button.setTooltip(new Tooltip(new ResourceBrowserViewModel(result.registry()).tooltip(entry,language)));button.setOnAction(event->{id.setText(entry.id().toString());mode.setValue("Exact Item");});grid.getChildren().add(button);
            }
            final var scroll=new ScrollPane(grid);scroll.setFitToWidth(true);return scroll;
        };
        final Runnable filter=()->{entries.clear();entries.addAll(new RegistrySearchService(result.registry()).search(kind,search.getText(),mods.getSelectionModel().getSelectedIndex()==0?"":mods.getValue()));list.setPageCount((entries.size()+29)/30);list.setCurrentPageIndex(0);list.setPageFactory(page);};
        search.textProperty().addListener((o,a,b)->filter.run()); mods.valueProperty().addListener((o,a,b)->filter.run()); filter.run();
        knownTags.setOnAction(e->{ if(knownTags.getValue()!=null) { id.setText(knownTags.getValue()); mode.setValue("Tag"); } });
        final Runnable refreshChoices=()->choices.getItems().setAll(selection.choices());
        final java.util.function.Supplier<String> current=()->new RecipeFormService().ingredient(ResourceLocation.parse(id.getText()),tag.isSelected());
        final Runnable preview=()->{
            variants.getChildren().clear();suggestions.getItems().clear();
            try {
                final var location=ResourceLocation.parse(id.getText());
                if(tag.isSelected()) {
                    final var members=result.recipes().tagIndex().members(kind,location).stream().sorted().toList();
                    variants.getChildren().add(new Label("Indexed tag members: "+members.size()+" (static load order may be unresolved)"));
                    members.stream().limit(8).forEach(member->variants.getChildren().add(new RecipeSlotView(RecipeSlotContent.parse(new RecipeFormService().ingredient(member,false)),result,textures,40)));
                } else result.recipes().tagIndex().containing(kind,location).stream().sorted().map(Object::toString).forEach(suggestions.getItems()::add);
            } catch(IllegalArgumentException invalid) {variants.getChildren().add(new Label("Select a valid item or tag ID"));}
        };
        id.textProperty().addListener((o,a,b)->{if(!loading[0])changed[0]=true;preview.run();});
        mode.valueProperty().addListener((o,a,b)->{if(!loading[0])changed[0]=true;preview.run();});
        suggestions.setOnAction(event->{if(suggestions.getValue()!=null){final String value=suggestions.getValue();mode.setValue("Tag");id.setText(value);}});
        choices.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            if(b==null)return;loading[0]=true;
            try{final var content=RecipeSlotContent.parse(b);id.setText(content.id()==null?"":content.id().toString());mode.setValue(content.kind()==RecipeSlotContent.Kind.TAG?"Tag":"Exact Item");if(allowAmount&&content.amount()>0&&content.amount()<=1000000)amount.getValueFactory().setValue((int)content.amount());changed[0]=false;}
            finally{loading[0]=false;}
        });
        addChoice.setOnAction(event->{try{selection.add(current.get());refreshChoices.run();choices.getSelectionModel().selectLast();changed[0]=false;error.setText("");}catch(RuntimeException invalid){error.setText(invalid.getMessage());}});
        applyChoice.setOnAction(event->{try{final int index=choices.getSelectionModel().getSelectedIndex();selection.replace(index,current.get());refreshChoices.run();choices.getSelectionModel().select(Math.max(0,index));changed[0]=false;error.setText("");}catch(RuntimeException invalid){error.setText(invalid.getMessage());}});
        removeChoice.setOnAction(event->{final int index=choices.getSelectionModel().getSelectedIndex();if(index>=0){selection.remove(index);refreshChoices.run();choices.getSelectionModel().selectFirst();changed[0]=false;}});
        if(!choices.getItems().isEmpty())choices.getSelectionModel().selectFirst();
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,event->{try{
            if(changed[0]||selection.choices().isEmpty())selection.replace(choices.getSelectionModel().getSelectedIndex(),current.get());
            selection.json();
            final int value=Integer.parseInt(amount.getEditor().getText());if(value<1||value>1000000)throw new IllegalArgumentException("Amount must be 1..1000000");amount.getValueFactory().setValue(value);
            if(!allowTag)ResourceLocation.parse(id.getText());
        }catch(RuntimeException invalid){error.setText(invalid.getMessage());event.consume();}});
        setResultConverter(button->{if(button!=ButtonType.OK)return null;final var content=RecipeSlotContent.parse(selection.json());return new Selection(content.id(),content.kind()==RecipeSlotContent.Kind.TAG,amount.getValue(),selection.json());});
    }
}
