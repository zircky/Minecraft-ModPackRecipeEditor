package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.util.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.util.Duration;

public final class RecipeEditorDialog extends Dialog<Void> {
    private final RecipeEditorService editor;
    private final dev.recipeeditor.integrations.api.IntegrationContext integration;
    private final RecipeEditorProvider modEditor = new GregTechRecipeEditorProvider();
    private final DiscoveryResult discovery;
    private final EditorTasks tasks;
    private final RecipeEditSession session;
    private final RecipeLayoutResolver layoutResolver=new RecipeLayoutResolver();
    private final RecipeFormService forms=new RecipeFormService();
    private final TextureResourceService textures=new TextureResourceService();
    private final TextArea raw=new TextArea(),diff=new TextArea();
    private final VBox visual=new VBox(12),advanced=new VBox(10);
    private final Label state=new Label(),issues=new Label();
    private final NotificationBanner notifications=new NotificationBanner();
    private final ButtonType save=new ButtonType("Save to project",ButtonBar.ButtonData.OTHER);
    private final PauseTransition debounce=new PauseTransition(Duration.millis(300));
    private final boolean creating;
    private RecipeEditPreview preview;
    private final Map<String,String> pendingAdvanced=new LinkedHashMap<>();
    private boolean alive=true,saving;
    private final Deque<String> draftUndo=new ArrayDeque<>(),draftRedo=new ArrayDeque<>();
    private boolean restoringDraft;
    private double slotSize=64;
    public RecipeEditorDialog(final Window owner,final DiscoveryResult discovery,final RecipeEditorService editor,final EditorTasks tasks,final RecipeEditSession session,final boolean creating, final dev.recipeeditor.integrations.api.IntegrationContext integration) {
        this.integration=integration;
        this.discovery=discovery; this.editor=editor; this.tasks=tasks; this.session=session; this.creating=creating;
        initOwner(owner); new UiTheme().apply(getDialogPane()); setTitle("Recipe Editor - "+session.id()); setResizable(true); getDialogPane().setPrefSize(1050,760);
        raw.setTextFormatter(new TextFormatter<String>(change->{ if(change.getControlNewText().length()>2*1024*1024) { issues.setText("Editor limit: 2 MiB characters; input rejected"); return null; } return change; }));
        raw.setId("recipe-raw-json");raw.setText(session.rawJson()); diff.setEditable(false); diff.setWrapText(true); raw.setWrapText(true);
        visual.setPadding(new Insets(16)); advanced.setPadding(new Insets(16));
        final var format=new Button("Format JSON"); format.setOnAction(e->patch(text->forms.format(text)));
        final var apply=new Button("Validate / Apply JSON"); apply.setOnAction(e->validateDraft());
        final var rawPane=new BorderPane(raw); rawPane.setTop(new HBox(10,format,apply,new Label("Unknown types: syntax/ID checks only; no semantic adapter.")));
        final var visualScroll=new ScrollPane(visual);visualScroll.setFitToWidth(true);
        final var advancedScroll=new ScrollPane(advanced);advancedScroll.setFitToWidth(true);
        final var tabs=new TabPane(tab("Visual",visualScroll),tab("Advanced",advancedScroll),tab("Raw JSON",rawPane),tab("Diff",diff));
        tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->session.mode(RecipeEditSession.Mode.values()[b.intValue()]));
        issues.setWrapText(true); issues.setMaxWidth(950); issues.getStyleClass().add("validation-error");
        final var warnings=new ScrollPane(issues); warnings.setFitToWidth(true); warnings.setPrefViewportHeight(100);
        final var undoDraft=new Button("Undo draft");undoDraft.setOnAction(event->restoreDraft(draftUndo,draftRedo));
        final var redoDraft=new Button("Redo draft");redoDraft.setOnAction(event->restoreDraft(draftRedo,draftUndo));
        final var content=new VBox(10,new Label("Recipe ID: "+session.id()+" · Source JARs remain read-only"),notifications,state,new HBox(8,undoDraft,redoDraft),tabs,warnings); VBox.setVgrow(tabs,Priority.ALWAYS); getDialogPane().setContent(content);
        final var resetDraft=new ButtonType("Reset draft",ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(save,resetDraft,ButtonType.CLOSE);
        getDialogPane().lookupButton(resetDraft).addEventFilter(javafx.event.ActionEvent.ACTION,event->{event.consume(); pendingAdvanced.clear(); session.cancel(); raw.setText(session.rawJson()); validateDraft();});
        getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION,event->{event.consume(); save();});
        raw.textProperty().addListener((o,a,b)->{
            notifications.dismiss();
            if(!restoringDraft&&!a.equals(b)){draftUndo.addFirst(a);while(draftUndo.size()>16)draftUndo.removeLast();draftRedo.clear();}
            try { session.setRawJson(b); preview=null; visual.getChildren().setAll(new Label("Raw JSON changed. Visual state is unavailable until validation completes.")); advanced.getChildren().clear(); getDialogPane().lookupButton(save).setDisable(true); state.setText("Unsaved editor changes"); debounce.playFromStart(); }
            catch(RuntimeException error) { issues.setText(error.getMessage()); getDialogPane().lookupButton(save).setDisable(true); }
        });
        debounce.setOnFinished(e->validateDraft());
        setOnCloseRequest(e->{ if(saving || ((session.dirty() || !pendingAdvanced.isEmpty()) && !confirmDiscard())) e.consume(); });
        setOnHidden(e->{alive=false; debounce.stop();textures.close();}); validateDraft();
    }
    private Tab tab(final String name,final javafx.scene.Node content) { final var tab=new Tab(name,content); tab.setClosable(false); return tab; }
    private boolean confirmDiscard() { final var alert=new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION,"Discard unsaved editor changes? Project changes already saved by this dialog remain in memory.",ButtonType.YES,ButtonType.NO)); alert.initOwner(getDialogPane().getScene().getWindow()); return alert.showAndWait().orElse(ButtonType.NO)==ButtonType.YES; }
    private void validateDraft() {
        final String text=raw.getText(); final long revision=session.revision(); getDialogPane().lookupButton(save).setDisable(true);
        tasks.submit(()->new RecipeDraftPreviewService().inspect(editor.validator(),session.id(),session.source(),session.originalJson(),text),value->{
            if(!alive || revision!=session.revision()) return; preview=value;
            issues.setText(new RecipeValidationPresenter().format(value));
            getDialogPane().lookupButton(save).setDisable(!value.validation().valid() || !pendingAdvanced.isEmpty()); state.setText(!pendingAdvanced.isEmpty()?"Unapplied Advanced fields: "+pendingAdvanced.keySet()+". Apply them or Reset draft before Save.":session.dirty()?"Unsaved editor changes":"Draft matches last Apply/Save · project file may still need Save Project");
            render(value); diff.setText(value.diffError().isEmpty()?value.differences().stream().map(d->d.path()+"\n- "+d.before()+"\n+ "+d.after()).collect(java.util.stream.Collectors.joining("\n\n")):value.diffError());
        },error->{if(alive && revision==session.revision()) issues.setText(error.getMessage());});
    }
    private void render(final RecipeEditPreview value) {
        visual.getChildren().clear(); advanced.getChildren().clear();
        if(value.validation().recipe()==null){visual.getChildren().add(new Label("Repair the recipe using Raw JSON; validation details are shown below."));return;}
        if(modEditor.supports(value.validation().recipe(),integration)) {
            visual.getChildren().add(modEditor.createEditor(new RecipeEditorContext(value.validation().recipe(),pendingAdvanced,
                (name,text)->{pendingAdvanced.put(name,text); state.setText("Unapplied GregTech fields: "+pendingAdvanced.keySet()); getDialogPane().lookupButton(save).setDisable(true);},
                this::applyGregTechField)));
            advanced.getChildren().add(new Label("Unknown properties are preserved. Unsupported properties block export rather than being omitted."));
            return;
        }
        if(!value.visualSupported()) { visual.getChildren().add(new Label("Visual editing unavailable. Raw JSON is preserved; unknown recipes have limited semantic validation.")); advanced.getChildren().add(new Label("No adapter for this type. Inspect and edit Raw JSON explicitly.")); return; }
        final var shared=layoutResolver.resolve(value.validation().recipe());
        if(shared.isPresent()) {
            final var actions=new RecipeLayoutRenderer.Actions(this::chooseInput,this::clearInput,this::chooseOutput);
            visual.setAlignment(javafx.geometry.Pos.TOP_CENTER);
            visual.getChildren().add(new RecipeLayoutRenderer(shared.get(),discovery,textures,slotSize,Locale.getDefault().getLanguage().equals("ru")?"ru_ru":"en_us",creating?RecipeLayoutRenderer.Mode.CREATOR:RecipeLayoutRenderer.Mode.EDITOR,actions));
            final var scale=new Spinner<Integer>(40,80,(int)slotSize,8);scale.setPrefWidth(100);scale.setAccessibleText("Recipe slot size in pixels");scale.valueProperty().addListener((o,a,b)->{slotSize=b;render(value);});
            final var sizing=new HBox(8,new Label("Slot size"),scale);sizing.setAlignment(javafx.geometry.Pos.CENTER);visual.getChildren().add(sizing);
            renderAdvancedFields(value);return;
        }
        visual.getChildren().add(new Label("No visual adapter for this recipe. Inspect Raw JSON."));
        renderAdvancedFields(value);
    }
    private void renderAdvancedFields(final RecipeEditPreview value) {
        final String type=value.type();
        final var fields=new ArrayList<>(List.of("group"));
        if(Set.of("minecraft:smelting","minecraft:blasting","minecraft:smoking","minecraft:campfire_cooking").contains(type)) fields.addAll(List.of("experience","cookingtime"));
        for(final var name:fields) advancedField(name,value.fields().getOrDefault(name,name.equals("group")?"\"\"":name.equals("experience")?"0":type.equals("minecraft:smelting")?"200":type.equals("minecraft:campfire_cooking")?"600":"100"));
        advanced.getChildren().add(new Label("Other / unknown properties are preserved. Use Raw JSON for explicit changes:"));
        value.fields().forEach((name,json)->{ if(!fields.contains(name)) { final var label=new Label(name+": "+brief(json)); label.setWrapText(true); label.setMaxWidth(850); advanced.getChildren().add(label); } });
    }
    private void applyGregTechField(final String pointer,final String json) {
        final String key="GT"+pointer, pending=pendingAdvanced.get(key), current=raw.getText();
        final long revision=session.revision();
        tasks.submit(()->new dev.recipeeditor.integrations.gregtech.GregTechRecipeEditing().replace(current,pointer,json),changed->{
            if(!alive || revision!=session.revision())return;
            if(Objects.equals(pendingAdvanced.get(key),pending))pendingAdvanced.remove(key);
            raw.setText(changed);
        },failure->{if(alive)issues.setText(failure.getMessage());});
    }
    private void chooseInput(final String role,final int index) {
        final String initial=role.equals("grid")?forms.grid(raw.getText()).get(index):role.equals("shapeless")?(index<forms.ingredientList(raw.getText()).size()?forms.ingredientList(raw.getText()).get(index):""):forms.fields(raw.getText()).getOrDefault(role,"");
        pickIngredient(initial,json->patch(text->{
            if(role.equals("grid")){final var values=new ArrayList<>(forms.grid(text));values.set(index,json);return forms.shaped(text,values);}
            if(role.equals("shapeless")){final var values=new ArrayList<>(forms.ingredientList(text));if(index<values.size())values.set(index,json);else values.add(json);return forms.shapeless(text,values);}
            return forms.patch(text,role,json);
        }));
    }
    private void clearInput(final String role,final int index) {
        patch(text->{if(role.equals("grid")){final var values=new ArrayList<>(forms.grid(text));values.set(index,"");return forms.shaped(text,values);}
            if(role.equals("shapeless")){final var values=new ArrayList<>(forms.ingredientList(text));if(index<values.size())values.remove(index);return forms.shapeless(text,values);}
            return forms.patch(text,role,"{}");});
    }
    private void chooseOutput() {
        final String type=forms.type(raw.getText());
        final var fields=forms.fields(raw.getText());
        String initial=fields.getOrDefault("result","");
        final var output=RecipeSlotContent.parse(initial);
        if(type.equals("minecraft:stonecutting")&&output.id()!=null)initial="{\"item\":\""+output.id()+"\",\"count\":"+fields.getOrDefault("count","1")+"}";
        final boolean cooking=new CookingRecipeLayoutProvider().find(type).isPresent();
        final boolean objectOutput=initial.stripLeading().startsWith("{");
        new IngredientPicker(getDialogPane().getScene().getWindow(),discovery,ResourceKind.ITEM,false,!cooking||objectOutput,initial).showAndWait().ifPresent(value->patch(text->forms.result(text,value.id(),value.amount())));
    }
    private void restoreDraft(final Deque<String> from,final Deque<String> to) {
        if(saving||from.isEmpty())return;
        if(!pendingAdvanced.isEmpty()){issues.setText("Apply Advanced fields or Reset draft before Undo/Redo.");return;}
        to.addFirst(raw.getText());restoringDraft=true;try{raw.setText(from.removeFirst());}finally{restoringDraft=false;}
    }
    private void pickIngredient(final String initial,final java.util.function.Consumer<String> selected) {
        new IngredientPicker(getDialogPane().getScene().getWindow(),discovery,ResourceKind.ITEM,true,false,initial).showAndWait().ifPresent(value->selected.accept(value.json()));
    }
    private void advancedField(final String name,final String value) {
        final var input=new TextField(pendingAdvanced.getOrDefault(name,value));
        input.textProperty().addListener((o,a,b)->{pendingAdvanced.put(name,b);state.setText("Unapplied Advanced fields: "+pendingAdvanced.keySet());getDialogPane().lookupButton(save).setDisable(true);}); input.setPrefWidth(380); final var error=new Label(preview==null?"":preview.validation().issues().stream().filter(issue->issue.message().toLowerCase(java.util.Locale.ROOT).contains(name)).map(ValidationIssue::message).collect(java.util.stream.Collectors.joining("\n"))); error.getStyleClass().add("validation-error");
        final var apply=new Button("Apply"); apply.setOnAction(e->{ final String valueText=input.getText(); final String current=raw.getText(); final long revision=session.revision();
            tasks.submit(()->forms.patch(current,name,valueText),changed->{ if(!alive || revision!=session.revision()) return; if(Objects.equals(pendingAdvanced.get(name),valueText)) pendingAdvanced.remove(name); raw.setText(changed); },failure->{ if(alive) {error.setText(failure.getMessage());issues.setText(failure.getMessage());} }); }); advanced.getChildren().add(new VBox(4,new HBox(10,new Label(name+" (JSON value)"),input,apply),error));
    }
    private String brief(final String value) {return value.length()>180?value.substring(0,180)+"... (full value in Raw JSON)":value;}
    private void patch(final java.util.function.UnaryOperator<String> work) {
        final long revision=session.revision(); final String current=raw.getText(); tasks.submit(()->work.apply(current),value->{if(alive && revision==session.revision()) raw.setText(value);},error->{if(alive) issues.setText(error.getMessage());});
    }
    private void save() {
        if(preview==null || !preview.validation().valid() || !pendingAdvanced.isEmpty()) return;
        final String text=raw.getText(); saving=true; getDialogPane().lookupButton(save).setDisable(true);
        tasks.submit(()->{ final var valid=editor.validator().validate(session.id(),text,session.source()); if(!valid.valid()) throw new IllegalArgumentException(valid.issues().toString());
            if(creating && editor.effective().findById(session.id()).isEmpty()) editor.create(valid.recipe()); else editor.update(session.id(),valid.recipe()); return text;
        },saved->{saving=false; session.markSaved(saved); validateDraft();notifications.show(NotificationBanner.Kind.SUCCESS,"Recipe saved to project memory. Use Save Project to persist it to disk.");},error->{saving=false; issues.setText(error.getMessage()); validateDraft();notifications.show(NotificationBanner.Kind.ERROR,"Recipe could not be saved. Review validation details.");});
    }
}
