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
    private final RecipeFormService forms=new RecipeFormService();
    private final TextArea raw=new TextArea(),diff=new TextArea();
    private final VBox visual=new VBox(12),advanced=new VBox(10);
    private final Label state=new Label(),issues=new Label();
    private final ButtonType save=new ButtonType("Save to project",ButtonBar.ButtonData.OTHER);
    private final PauseTransition debounce=new PauseTransition(Duration.millis(300));
    private final boolean creating;
    private RecipeEditPreview preview;
    private final Map<String,String> pendingAdvanced=new LinkedHashMap<>();
    private boolean alive=true,saving;
    public RecipeEditorDialog(final Window owner,final DiscoveryResult discovery,final RecipeEditorService editor,final EditorTasks tasks,final RecipeEditSession session,final boolean creating, final dev.recipeeditor.integrations.api.IntegrationContext integration) {
        this.integration=integration;
        this.discovery=discovery; this.editor=editor; this.tasks=tasks; this.session=session; this.creating=creating;
        initOwner(owner); getDialogPane().getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("theme.css")).toExternalForm()); setTitle("Recipe Editor - "+session.id()); setResizable(true); getDialogPane().setPrefSize(1050,760);
        raw.setTextFormatter(new TextFormatter<String>(change->{ if(change.getControlNewText().length()>2*1024*1024) { issues.setText("Editor limit: 2 MiB characters; input rejected"); return null; } return change; }));
        raw.setText(session.rawJson()); diff.setEditable(false); diff.setWrapText(true); raw.setWrapText(true);
        visual.setPadding(new Insets(16)); advanced.setPadding(new Insets(16));
        final var format=new Button("Format JSON"); format.setOnAction(e->patch(text->forms.format(text)));
        final var apply=new Button("Validate / Apply JSON"); apply.setOnAction(e->validateDraft());
        final var rawPane=new BorderPane(raw); rawPane.setTop(new HBox(10,format,apply,new Label("Unknown types: syntax/ID checks only; no semantic adapter.")));
        final var tabs=new TabPane(tab("Visual",new ScrollPane(visual)),tab("Advanced",new ScrollPane(advanced)),tab("Raw JSON",rawPane),tab("Diff",diff));
        tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->session.mode(RecipeEditSession.Mode.values()[b.intValue()]));
        issues.setWrapText(true); issues.setMaxWidth(950); issues.getStyleClass().add("validation-error");
        final var warnings=new ScrollPane(issues); warnings.setFitToWidth(true); warnings.setPrefViewportHeight(100);
        final var content=new VBox(10,new Label("Recipe ID: "+session.id()+" · Source JARs remain read-only"),state,tabs,warnings); VBox.setVgrow(tabs,Priority.ALWAYS); getDialogPane().setContent(content);
        final var resetDraft=new ButtonType("Reset draft",ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(save,resetDraft,ButtonType.CLOSE);
        getDialogPane().lookupButton(resetDraft).addEventFilter(javafx.event.ActionEvent.ACTION,event->{event.consume(); pendingAdvanced.clear(); session.cancel(); raw.setText(session.rawJson()); validateDraft();});
        getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION,event->{event.consume(); save();});
        raw.textProperty().addListener((o,a,b)->{
            try { session.setRawJson(b); preview=null; visual.getChildren().setAll(new Label("Raw JSON changed. Visual state is unavailable until validation completes.")); advanced.getChildren().clear(); getDialogPane().lookupButton(save).setDisable(true); state.setText("Unsaved editor changes"); debounce.playFromStart(); }
            catch(RuntimeException error) { issues.setText(error.getMessage()); getDialogPane().lookupButton(save).setDisable(true); }
        });
        debounce.setOnFinished(e->validateDraft());
        setOnCloseRequest(e->{ if(saving || ((session.dirty() || !pendingAdvanced.isEmpty()) && !confirmDiscard())) e.consume(); });
        setOnHidden(e->{alive=false; debounce.stop();}); validateDraft();
    }
    private Tab tab(final String name,final javafx.scene.Node content) { final var tab=new Tab(name,content); tab.setClosable(false); return tab; }
    private boolean confirmDiscard() { final var alert=new Alert(Alert.AlertType.CONFIRMATION,"Discard unsaved editor changes? Project changes already saved by this dialog remain in memory.",ButtonType.YES,ButtonType.NO); alert.initOwner(getDialogPane().getScene().getWindow()); return alert.showAndWait().orElse(ButtonType.NO)==ButtonType.YES; }
    private void validateDraft() {
        final String text=raw.getText(); final long revision=session.revision(); getDialogPane().lookupButton(save).setDisable(true);
        tasks.submit(()->new RecipeDraftPreviewService().inspect(editor.validator(),session.id(),session.source(),session.originalJson(),text),value->{
            if(!alive || revision!=session.revision()) return; preview=value;
            issues.setText(value.validation().issues().stream().map(issue->issue.severity()+" ["+issue.field()+"] "+issue.message()).collect(java.util.stream.Collectors.joining("\n")));
            getDialogPane().lookupButton(save).setDisable(!value.validation().valid() || !pendingAdvanced.isEmpty()); state.setText(!pendingAdvanced.isEmpty()?"Unapplied Advanced fields: "+pendingAdvanced.keySet()+". Apply them or Reset draft before Save.":session.dirty()?"Unsaved editor changes":"Draft matches last Apply/Save · project file may still need Save Project");
            render(value); diff.setText(value.diffError().isEmpty()?value.differences().stream().map(d->d.path()+"\n- "+d.before()+"\n+ "+d.after()).collect(java.util.stream.Collectors.joining("\n\n")):value.diffError());
        },error->{if(alive && revision==session.revision()) issues.setText(error.getMessage());});
    }
    private void render(final RecipeEditPreview value) {
        visual.getChildren().clear(); advanced.getChildren().clear();
        if(modEditor.supports(value.validation().recipe(),integration)) {
            visual.getChildren().add(modEditor.createEditor(new RecipeEditorContext(value.validation().recipe(),pendingAdvanced,
                (name,text)->{pendingAdvanced.put(name,text); state.setText("Unapplied GregTech fields: "+pendingAdvanced.keySet()); getDialogPane().lookupButton(save).setDisable(true);},
                this::applyGregTechField)));
            advanced.getChildren().add(new Label("Unknown properties are preserved. Unsupported properties block export rather than being omitted."));
            return;
        }
        if(!value.visualSupported()) { visual.getChildren().add(new Label("Visual editing unavailable. Raw JSON is preserved; unknown recipes have limited semantic validation.")); advanced.getChildren().add(new Label("No adapter for this type. Inspect and edit Raw JSON explicitly.")); return; }
        final String type=value.type(); visual.getChildren().add(new Label(type));
        if(type.equals("minecraft:crafting_shaped")) shaped(value);
        else if(type.equals("minecraft:crafting_shapeless")) shapeless(value);
        else if(type.startsWith("minecraft:smithing_")) for(final var role:List.of("template","base","addition")) ingredientButton(role,value.fields().getOrDefault(role,""));
        else ingredientButton("ingredient",value.fields().getOrDefault("ingredient",""));
        visual.getChildren().add(new RecipeOutputEditor(getDialogPane().getScene().getWindow(),discovery,value.validation().recipe(),(id,count)->patch(text->forms.result(text,id,count)),()->patch(text->forms.removeResult(text))));
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
    private void shaped(final RecipeEditPreview value) {
        final var slots=new ArrayList<>(value.grid()); final var grid=new GridPane(); grid.setHgap(8); grid.setVgap(8);
        while(slots.size()<9) slots.add("");
        for(int i=0;i<9;i++) { final int index=i; final var button=new Button(slots.get(i).isBlank()?"Empty":brief(slots.get(i))); button.setWrapText(true); button.setPrefSize(210,80);
            button.setOnAction(e->pickIngredient(json->{ slots.set(index,json); patch(text->forms.shaped(text,List.copyOf(slots))); }));
            final var clear=new MenuItem("Clear slot"); clear.setOnAction(e->{ slots.set(index,""); patch(text->forms.shaped(text,List.copyOf(slots))); }); button.setContextMenu(new ContextMenu(clear)); grid.add(button,i%3,i/3);
        } visual.getChildren().addAll(new Label("Click to replace; right-click to clear. Empty slots are not ingredients."),grid);
    }
    private void shapeless(final RecipeEditPreview value) {
        final var values=new ArrayList<>(value.shapeless());
        for(int i=0;i<values.size();i++) { final int index=i; final var row=new HBox(8); final var edit=new Button(brief(values.get(i))); edit.setMaxWidth(650); edit.setOnAction(e->pickIngredient(json->{values.set(index,json); patch(text->forms.shapeless(text,List.copyOf(values)));}));
            final var remove=new Button("Remove"); remove.setOnAction(e->{values.remove(index); patch(text->forms.shapeless(text,List.copyOf(values)));}); row.getChildren().addAll(edit,remove); visual.getChildren().add(row); }
        final var add=new Button("Add ingredient"); add.setDisable(values.size()>=9); add.setOnAction(e->pickIngredient(json->{values.add(json); patch(text->forms.shapeless(text,List.copyOf(values)));})); visual.getChildren().add(add);
    }
    private void ingredientButton(final String field,final String value) { final var button=new Button(field+": "+brief(value)); button.setWrapText(true); button.setOnAction(e->pickIngredient(json->patch(text->forms.patch(text,field,json)))); visual.getChildren().add(button); }
    private void pickIngredient(final java.util.function.Consumer<String> selected) {
        new IngredientPicker(getDialogPane().getScene().getWindow(),discovery,ResourceKind.ITEM,true,false).showAndWait().ifPresent(value->selected.accept(value.json()));
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
        },saved->{saving=false; session.markSaved(saved); validateDraft();},error->{saving=false; issues.setText(error.getMessage()); validateDraft();});
    }
}
