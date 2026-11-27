package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.integrations.gregtech.*;
import java.util.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

final class RecipeEditorSidebar extends BorderPane implements AutoCloseable {
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
    private final Label identity=new Label();
    private final NotificationBanner notifications=new NotificationBanner();
    private final Button saveButton=new Button("Save to project");
    private final PauseTransition debounce=new PauseTransition(Duration.millis(300));
    private final boolean creating;
    private RecipeEditPreview preview;
    private final Map<String,String> pendingAdvanced=new LinkedHashMap<>();
    private boolean alive=true,saving;
    private final Deque<String> draftUndo=new ArrayDeque<>(),draftRedo=new ArrayDeque<>();
    private boolean restoringDraft;
    private double slotSize=64;
    private List<GregTechMachineCatalog.Machine> machines=List.of();
    private final TextField migrationId=new TextField();
    private long validationSequence;
    private boolean migrationSaved;
    private String language=Locale.getDefault().getLanguage().equals("ru")?"ru_ru":"en_us";
    RecipeEditorSidebar(final DiscoveryResult discovery,final RecipeEditorService editor,final EditorTasks tasks,final RecipeEditSession session,final boolean creating, final dev.recipeeditor.integrations.api.IntegrationContext integration) {
        this.integration=integration;
        this.discovery=discovery; this.editor=editor; this.tasks=tasks; this.session=session; this.creating=creating;
        getStyleClass().add("workspace-view");
        setMinWidth(280);
        migrationId.setPromptText("Explicit target recipe ID");
        migrationId.textProperty().addListener((o,a,b)->{saveButton.setDisable(true);debounce.playFromStart();});
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
        final var resetDraft=new Button("Reset draft");
        resetDraft.setOnAction(event->{pendingAdvanced.clear();session.cancel();raw.setText(session.rawJson());validateDraft();});
        saveButton.setOnAction(event->save());
        final var actions=new FlowPane(8,6,saveButton,resetDraft,undoDraft,redoDraft);
        identity.setWrapText(true);
        identity.setText("Recipe ID: "+session.id());
        final var header=new VBox(8,identity,notifications,state,actions);
        header.getStyleClass().add("view-header");
        setTop(header);
        setCenter(tabs);
        setBottom(warnings);
        raw.textProperty().addListener((o,a,b)->{
            notifications.dismiss();
            if(!restoringDraft&&!a.equals(b)){draftUndo.addFirst(a);while(draftUndo.size()>16)draftUndo.removeLast();draftRedo.clear();}
            try { session.setRawJson(b); preview=null; visual.getChildren().setAll(new Label("Raw JSON changed. Visual state is unavailable until validation completes.")); advanced.getChildren().clear(); saveButton.setDisable(true); state.setText("Unsaved editor changes"); debounce.playFromStart(); }
            catch(RuntimeException error) { issues.setText(error.getMessage()); saveButton.setDisable(true); }
        });
        debounce.setOnFinished(e->validateDraft());
        validateDraft();
    }
    boolean allowClose() { return !saving && (!(session.dirty() || !pendingAdvanced.isEmpty()) || confirmDiscard()); }
    void language(final String value) {
        language=Objects.requireNonNull(value);
        if(preview!=null)render(preview);
    }
    @Override public void close() { alive=false; debounce.stop();textures.close(); }
    void action(final Button button) {
        final var header=(VBox)getTop();
        ((FlowPane)header.getChildren().getLast()).getChildren().add(button);
    }
    void machines(final List<GregTechMachineCatalog.Machine> values) {
        machines=List.copyOf(values);
        validateDraft();
    }
    void sourceMod(final String value) { identity.setText("Recipe ID: "+session.id()+" · Source mod: "+value); }
    private Tab tab(final String name,final javafx.scene.Node content) { final var tab=new Tab(name,content); tab.setClosable(false); return tab; }
    private boolean confirmDiscard() { final var alert=new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION,"Discard unsaved editor changes? Project changes already saved by this dialog remain in memory.",ButtonType.YES,ButtonType.NO)); alert.initOwner(getScene().getWindow()); return alert.showAndWait().orElse(ButtonType.NO)==ButtonType.YES; }
    private void validateDraft() {
        final String text=raw.getText(), target=migrationId.getText();
        final long revision=session.revision(), check=++validationSequence;
        final var catalog=machines;
        saveButton.setDisable(true);
        tasks.submit(()->{
            final var value=new RecipeDraftPreviewService().inspect(editor.validator(),session.id(),session.source(),session.originalJson(),text);
            return new Checked(value,checkMigration(text,target,catalog));
        },checked->{
            if(!alive || revision!=session.revision() || check!=validationSequence) return;
            final var value=checked.preview();
            preview=value;
            issues.setText(new RecipeValidationPresenter().format(value)+"\n"+String.join("\n",checked.migration().issues()));
            saveButton.setDisable(migrationSaved || !canAuthor(value.validation().recipe()) || !value.validation().valid()
                    || !pendingAdvanced.isEmpty() || !checked.migration().issues().isEmpty());
            raw.setEditable(!migrationSaved && canAuthor(value.validation().recipe())); state.setText(!pendingAdvanced.isEmpty()?"Unapplied Advanced fields: "+pendingAdvanced.keySet()+". Apply them or Reset draft before Save.":session.dirty()?"Unsaved editor changes":"Draft matches last Apply/Save · project file may still need Save Project");
            render(value); diff.setText(value.diffError().isEmpty()?value.differences().stream().map(d->d.path()+"\n- "+d.before()+"\n+ "+d.after()).collect(java.util.stream.Collectors.joining("\n\n")):value.diffError());
        },error->{if(alive && revision==session.revision()) issues.setText(error.getMessage());});
    }

    private record Migration(ResourceLocation target, List<String> issues) {}
    private record Checked(RecipeEditPreview preview, Migration migration) {}

    private Migration checkMigration(final String text,final String targetText,
            final List<GregTechMachineCatalog.Machine> catalog) {
        try {
            final String original=forms.type(session.originalJson()), changed=forms.type(text);
            if(original.equals(changed) && changed.startsWith("gtceu:") && (creating ||
                    editor.authoringBases().findById(session.id()).stream().anyMatch(base->new RuntimeRecipeBase().matches(base.source())))) {
                final var issues=new GregTechRecipeExporter().unsupported(new GregTechRecipeMapper().map(session.id(),text,false));
                return new Migration(session.id(),issues);
            }
            if(original.equals(changed) || (!original.startsWith("gtceu:") && catalog.stream().noneMatch(m->m.id().toString().equals(original))))
                return new Migration(session.id(),List.of());
            final var machine=catalog.stream().filter(row->row.id().toString().equals(changed)).findFirst();
            if(machine.isEmpty())return new Migration(session.id(),List.of("The target machine is not confirmed in the runtime snapshot."));
            final var target=ResourceLocation.parse(targetText.strip());
            final var result=new RecipeTypeMigrationService().preview(text,target,machine.orElseThrow(),integration);
            final var problems=new ArrayList<>(result.issues());
            if(target.equals(session.id()))problems.add("The target KubeJS builder requires an explicit new recipe ID.");
            return new Migration(target,problems);
        } catch(RuntimeException failure) {
            return new Migration(session.id(),List.of(Objects.toString(failure.getMessage(),"Invalid machine migration or target ID")));
        }
    }
    private void render(final RecipeEditPreview value) {
        visual.getChildren().clear(); advanced.getChildren().clear();
        if(value.validation().recipe()==null){visual.getChildren().add(new Label("Repair the recipe using Raw JSON; validation details are shown below."));return;}
        if(!canAuthor(value.validation().recipe())) {
            visual.getChildren().add(new Label("Read-only mod adapter: authoring/export require a separately verified implementation."));
            layoutResolver.resolve(value.validation().recipe()).ifPresent(model->visual.getChildren().add(new RecipeLayoutRenderer(model,discovery,textures,slotSize,language,RecipeLayoutRenderer.Mode.VIEWER,null)));
            return;
        }
        if(modEditor.supports(value.validation().recipe(),integration)) {
            final var selected=ResourceLocation.parse(value.type());
            visual.getChildren().add(new GregTechMachineSelector(machines,selected,machine->{
                if(!pendingAdvanced.isEmpty()) { issues.setText("Apply or reset pending fields before changing the machine.");return; }
                patch(text->{
                    final var root=(com.fasterxml.jackson.databind.node.ObjectNode)new GregTechRecipeMapper().object(text).deepCopy();
                    root.put("type",machine.id().toString());
                    return root.toString();
                });
            },!migrationSaved));
            final var target=machines.stream().filter(machine->machine.id().equals(selected)).findFirst();
            target.ifPresent(machine->visual.getChildren().add(slotLimits(machine,value.validation().recipe())));
            if(!forms.type(session.originalJson()).equals(value.type())) {
                if(migrationId.getParent() instanceof Pane pane)pane.getChildren().remove(migrationId);
                visual.getChildren().add(new VBox(5,new Label("Target ID · namespace:"+selected.path()+"/name"),migrationId));
            }
            visual.getChildren().add(modEditor.createEditor(new RecipeEditorContext(value.validation().recipe(),pendingAdvanced,
                (name,text)->{pendingAdvanced.put(name,text); state.setText("Unapplied GregTech fields: "+pendingAdvanced.keySet()); saveButton.setDisable(true);},
                this::applyGregTechField)));
            advanced.getChildren().add(new Label("Unknown properties are preserved. Unsupported properties block export rather than being omitted."));
            return;
        }
        if(!value.visualSupported()) { visual.getChildren().add(new Label("Visual editing unavailable. Raw JSON is preserved; unknown recipes have limited semantic validation.")); advanced.getChildren().add(new Label("No adapter for this type. Inspect and edit Raw JSON explicitly.")); return; }
        final var shared=layoutResolver.resolve(value.validation().recipe());
        if(shared.isPresent()) {
            final var actions=new RecipeLayoutRenderer.Actions(this::chooseInput,this::clearInput,this::chooseOutput);
            visual.setAlignment(javafx.geometry.Pos.TOP_CENTER);
            visual.getChildren().add(new RecipeLayoutRenderer(shared.get(),discovery,textures,slotSize,language,creating?RecipeLayoutRenderer.Mode.CREATOR:RecipeLayoutRenderer.Mode.EDITOR,actions));
            final var scale=new Spinner<Integer>(40,80,(int)slotSize,8);scale.setPrefWidth(100);scale.setAccessibleText("Recipe slot size in pixels");scale.valueProperty().addListener((o,a,b)->{slotSize=b;render(value);});
            final var sizing=new HBox(8,new Label("Slot size"),scale);sizing.setAlignment(javafx.geometry.Pos.CENTER);visual.getChildren().add(sizing);
            renderAdvancedFields(value);return;
        }
        visual.getChildren().add(new Label("No visual adapter for this recipe. Inspect Raw JSON."));
        renderAdvancedFields(value);
    }
    private void renderAdvancedFields(final RecipeEditPreview value) {
        final var schema=new VanillaRecipeSchema().fields(value.type());
        final var fields=schema.stream().map(VanillaRecipeSchema.Field::key).collect(java.util.stream.Collectors.toSet());
        for(final var field:schema) advancedField(field,value.fields().getOrDefault(field.key(),field.defaultJson()));
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
        new IngredientPicker(getScene().getWindow(),discovery,ResourceKind.ITEM,false,!cooking||objectOutput,initial,language).showAndWait().ifPresent(value->patch(text->forms.result(text,value.id(),value.amount())));
    }
    private void restoreDraft(final Deque<String> from,final Deque<String> to) {
        if(saving||from.isEmpty())return;
        if(!pendingAdvanced.isEmpty()){issues.setText("Apply Advanced fields or Reset draft before Undo/Redo.");return;}
        to.addFirst(raw.getText());restoringDraft=true;try{raw.setText(from.removeFirst());}finally{restoringDraft=false;}
    }
    private void pickIngredient(final String initial,final java.util.function.Consumer<String> selected) {
        new IngredientPicker(getScene().getWindow(),discovery,ResourceKind.ITEM,true,false,initial,language).showAndWait().ifPresent(value->selected.accept(value.json()));
    }
    private void advancedField(final VanillaRecipeSchema.Field field,final String value) {
        final String name=field.key();
        final var pending=new javafx.beans.property.SimpleStringProperty(pendingAdvanced.getOrDefault(name,value));
        final var input=fieldControl(field,pending);
        input.setAccessibleText(name);
        pending.addListener((o,a,b)->{pendingAdvanced.put(name,b);state.setText("Unapplied Advanced fields: "+pendingAdvanced.keySet());saveButton.setDisable(true);});
        final var error=new Label(preview==null?"":preview.validation().issues().stream().filter(issue->issue.message().toLowerCase(java.util.Locale.ROOT).contains(name)).map(ValidationIssue::message).collect(java.util.stream.Collectors.joining("\n"))); error.getStyleClass().add("validation-error");
        final var apply=new Button("Apply"); apply.setOnAction(e->{
            try {
                if(input instanceof Spinner<?> spinner) spinner.commitValue();
            } catch(RuntimeException invalid) { error.setText("Enter a valid value for "+name);return; }
            final String valueText=pending.get(); final String current=raw.getText(); final long revision=session.revision();
            tasks.submit(()->forms.patch(current,name,valueText),changed->{ if(!alive || revision!=session.revision()) return; if(Objects.equals(pendingAdvanced.get(name),valueText)) pendingAdvanced.remove(name); raw.setText(changed); },failure->{ if(alive) {error.setText(failure.getMessage());issues.setText(failure.getMessage());} }); }); advanced.getChildren().add(new VBox(4,new HBox(10,new Label(name),input,apply),error));
    }

    private Control fieldControl(final VanillaRecipeSchema.Field field,final javafx.beans.property.StringProperty value) {
        try {
            final var parsed=com.google.gson.JsonParser.parseString(value.get());
            if (field.kind()==VanillaRecipeSchema.Kind.BOOLEAN && parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isBoolean()) {
                final var check=new CheckBox();
                check.setSelected(parsed.getAsBoolean());
                check.selectedProperty().addListener((o,a,b)->value.set(Boolean.toString(b)));
                return check;
            }
            if (field.kind()==VanillaRecipeSchema.Kind.ENUM && parsed.isJsonPrimitive()
                    && field.choices().contains(parsed.getAsString())) {
                final var choices=new ComboBox<String>(javafx.collections.FXCollections.observableArrayList(field.choices()));
                choices.setValue(parsed.getAsString());
                choices.valueProperty().addListener((o,a,b)->value.set(new com.google.gson.JsonPrimitive(b).toString()));
                return choices;
            }
            if (field.kind()==VanillaRecipeSchema.Kind.STRING && parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()) {
                final var text=new TextField(parsed.getAsString());
                text.textProperty().addListener((o,a,b)->value.set(new com.google.gson.JsonPrimitive(b).toString()));
                return text;
            }
            if (field.kind()==VanillaRecipeSchema.Kind.INTEGER && parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isNumber()) {
                final int number=parsed.getAsBigDecimal().intValueExact();
                if(number>0) {
                    final var spinner=new Spinner<Integer>(1,Integer.MAX_VALUE,number);
                    spinner.setEditable(true);
                    spinner.getEditor().textProperty().addListener((o,a,b)->value.set(b));
                    spinner.valueProperty().addListener((o,a,b)->value.set(b.toString()));
                    return spinner;
                }
            }
        } catch (RuntimeException malformed) {
            // Keep invalid or unrecognized input visible so the user can repair it.
        }
        final var text=new TextField(value.get());
        text.textProperty().bindBidirectional(value);
        return text;
    }
    private String brief(final String value) {return value.length()>180?value.substring(0,180)+"... (full value in Raw JSON)":value;}
    private void patch(final java.util.function.UnaryOperator<String> work) {
        final long revision=session.revision(); final String current=raw.getText(); tasks.submit(()->work.apply(current),value->{if(alive && revision==session.revision()) raw.setText(value);},error->{if(alive) issues.setText(error.getMessage());});
    }
    private boolean canAuthor(final RecipeDefinition recipe) {
        if(recipe==null)return true;
        if(recipe.type().displayId().startsWith("gtceu:"))
            return new GregTechIntegration().capabilities(integration).enabled("Visual Editing");
        return recipe.type().location().flatMap(new dev.recipeeditor.integrations.ProjectRecipeAdapters().registry()::find).map(adapter->adapter.capabilities(recipe).supports(dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_EDIT)).orElse(true);
    }
    private void save() {
        if(migrationSaved || preview==null || !canAuthor(preview.validation().recipe()) || !preview.validation().valid() || !pendingAdvanced.isEmpty()) return;
        final String text=raw.getText(), target=migrationId.getText(); saving=true; saveButton.setDisable(true);
        final var catalog=machines;
        tasks.submit(()->{
            final var migration=checkMigration(text,target,catalog);
            if(!migration.issues().isEmpty())throw new IllegalArgumentException(String.join("\n",migration.issues()));
            final var valid=editor.validator().validate(migration.target(),text,session.source());
            if(!valid.valid())throw new IllegalArgumentException(valid.issues().toString());
            final boolean moved=!session.id().equals(migration.target());
            if(moved && (!creating || editor.status(session.id())==RecipeModificationStatus.CREATED)) editor.migrate(session.id(),valid.recipe());
            else if(creating && editor.effective().findById(session.id()).isEmpty()) editor.create(valid.recipe());
            else editor.update(session.id(),valid.recipe());
            return moved;
        },moved->{saving=false;migrationSaved=moved;session.markSaved(text);validateDraft();notifications.show(NotificationBanner.Kind.SUCCESS,"Recipe saved to project memory. Use Save Project to persist it to disk.");},error->{saving=false;issues.setText(error.getMessage());saveButton.setDisable(false);notifications.show(NotificationBanner.Kind.ERROR,"Recipe could not be saved. Review validation details.");});
    }

    private VBox slotLimits(final GregTechMachineCatalog.Machine machine,final RecipeDefinition recipe) {
        final var container=new VBox(6);
        final var json=new GregTechRecipeMapper().object(recipe.rawJson());
        final var editing=new GregTechSlotEditing();
        for(final String role:List.of("inputs","outputs"))for(final String capability:List.of("item","fluid")) {
            final var max=machine.limits().get(role+"."+capability);
            if(max==null)continue;
            final var entries=json.path(role).path(capability);
            final int count=entries.size();
            final var slots=new FlowPane(4,4);
            for(int i=0;i<Math.min(32,Math.max(count,max));i++) {
                final int index=i;
                final String initial=i<count?editing.flat(entries.get(i).path("content"),capability):"";
                final var slot=new Button();
                if(!initial.isEmpty())slot.setGraphic(new RecipeSlotView(RecipeSlotContent.parse(initial),discovery,textures,40,language));
                else slot.setText(i<count?"Complex":"＋");
                slot.setAccessibleText(role+" "+capability+" slot "+(i+1));
                slot.setDisable(migrationSaved || i>count || i>=max || (i<count && initial.isBlank()));
                slot.setOnAction(event->{
                    if(!pendingAdvanced.isEmpty()){issues.setText("Apply or reset pending fields first.");return;}
                    final var kind=capability.equals("fluid")?ResourceKind.FLUID:ResourceKind.ITEM;
                    new IngredientPicker(getScene().getWindow(),discovery,kind,true,true,initial,language)
                            .showAndWait().ifPresent(pick->patch(text->editing.replace(text,role,capability,index,max,pick.json(),pick.amount())));
                });
                final var cell=new VBox(2,slot);
                if(i<count&&!migrationSaved) {
                    final var remove=new Button("Remove");
                    remove.setOnAction(event->{
                        if(pendingAdvanced.isEmpty())patch(text->editing.remove(text,role,capability,index));
                        else issues.setText("Apply or reset pending fields first.");
                    });
                    cell.getChildren().add(remove);
                }
                slots.getChildren().add(cell);
            }
            container.getChildren().addAll(new Label(role+" · "+capability+"   "+count+" / "+max),slots);
        }
        return container;
    }
}
