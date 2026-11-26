package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.SourceType;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.*;

public final class RecipeBrowserView extends BorderPane implements AutoCloseable {
  private final java.util.function.BiConsumer<RecipeDefinition, dev.recipeeditor.exporters.ExportFormat> exportRecipe;
  private DiscoveryResult editorDiscovery;
  private List<dev.recipeeditor.integrations.gregtech.GregTechMachineCatalog.Machine> machines=List.of();
  private java.util.function.BiConsumer<dev.recipeeditor.registry.ResourceKind,ResourceLocation> resourceNavigation=(kind,id)->{};
  public void resourceNavigation(final java.util.function.BiConsumer<dev.recipeeditor.registry.ResourceKind,ResourceLocation> action){resourceNavigation=Objects.requireNonNull(action);}
  private final dev.recipeeditor.registry.TextureResourceService textures=new dev.recipeeditor.registry.TextureResourceService();
  private final dev.recipeeditor.integrations.api.IntegrationContext integration;
  private final dev.recipeeditor.integrations.api.ModdedRecipeAdapterRegistry moddedAdapters=new dev.recipeeditor.integrations.ProjectRecipeAdapters().registry();
  private final RecipeTypeDisplayNameResolver typeNames=new RecipeTypeDisplayNameResolver();
  private RecipeDisplayNameResolver displayNames;
  private final Map<RecipeDefinition,RecipeDisplayNameResolver.Display> displayCache=new IdentityHashMap<>();
  private final ComboBox<String> language=new ComboBox<>();
  private Set<ResourceLocation> runtimeIds = Set.of();
  private final TextField search = new TextField();
  private final ComboBox<String> mod = new ComboBox<>(), type = new ComboBox<>();
  private final ComboBox<RecipeFilterMode> modMode = new ComboBox<>(FXCollections.observableArrayList(RecipeFilterMode.values()));
  private final TableView<RecipeDefinition> table = new TableView<>();
  private final VBox viewer = new VBox(10);
  private final TextArea json = area(), source = area(), diagnostics = area();
  private final ComboBox<String> jsonVersion=new ComboBox<>();
  private final Map<String,String> jsonPayloads=new LinkedHashMap<>();
  private final Label scope = new Label();
  private final RecipeEditorService editor;
  private final EditorTasks tasks;
  private final javafx.collections.ObservableList<RecipeDefinition> rows = FXCollections.observableArrayList();
  private final javafx.collections.transformation.FilteredList<RecipeDefinition> filtered = new javafx.collections.transformation.FilteredList<>(rows);
  private final ComboBox<String> modification = new ComboBox<>();
  private final Label tracking = new Label();
  private final java.util.function.Consumer<ResourceLocation> listener;
  private final java.util.List<Button> historyButtons = new ArrayList<>();
  private final Button editButton=new Button("Edit"), duplicateButton=new Button("Duplicate"), deleteButton=new Button("Delete"),
      resetButton=new Button("Reset changes");
  private ResourceLocation scopeItem;
  private dev.recipeeditor.registry.ResourceKind scopeKind = dev.recipeeditor.registry.ResourceKind.ITEM;
  private boolean scopeUses, closed;
  private TabPane detailTabs;
  private final ComboBox<String> sourceFilter = new ComboBox<>();
  private SplitPane split;
  private RuntimeRecipeCatalog.Catalog runtime;
  private RecipeEditorSidebar staticDraft;
  private boolean restoringSelection;
  private ResourceLocation selectionAnchor;
  private long filterRevision;
  private RecipeDisplayNameResolver indexedNames;
  private String indexedLanguage;
  private final Map<RecipeDefinition,String> searchIndex=new IdentityHashMap<>();
  private java.util.concurrent.Future<?> pendingFilter;
  private final java.util.concurrent.ExecutorService filterWorker = java.util.concurrent.Executors.newSingleThreadExecutor(
      Thread.ofVirtual().name("recipe-catalog-filter").factory());
  private java.nio.file.Path projectFile;

  public void projectFile(final java.nio.file.Path file) {
    projectFile = file;
  }

  public RecipeBrowserView(final DiscoveryResult result, final RecipeEditorService editor, final EditorTasks tasks, final java.util.function.BiConsumer<RecipeDefinition, dev.recipeeditor.exporters.ExportFormat> exportRecipe, final dev.recipeeditor.integrations.api.IntegrationContext integration) {
    this.integration = integration;
    this.exportRecipe = exportRecipe;
    editorDiscovery = result;
    displayNames=new RecipeDisplayNameResolver(result.registry(),typeNames);
    this.editor = editor;
    this.tasks = tasks;
    getStyleClass().add("workspace-view");
    rows.setAll(catalogRows("All"));
    listener = id -> Platform.runLater(() -> refresh(id));
    editor.addListener(listener);
    search.setPromptText("Search result name, Recipe ID, type, machine or source");
    search.setId("recipe-search");language.getItems().setAll("en_us","ru_ru");language.setValue(Locale.getDefault().getLanguage().equals("ru")?"ru_ru":"en_us");
    language.setAccessibleText("Recipe display language");
    HBox.setHgrow(search, Priority.ALWAYS);
    modMode.setValue(RecipeFilterMode.SOURCE_MOD);
    updateModOptions();
    type.getItems().add("All types");
    result.recipes().recipes().stream().map(r -> r.type().displayId()).filter(id -> !id.isBlank()).distinct().sorted().forEach(type.getItems()::add);
    type.getSelectionModel().selectFirst();
    final var reset = new Button("All recipes");
    reset.setOnAction(e -> all());
    modification.getItems().addAll("All changes", "UNCHANGED", "CREATED", "MODIFIED", "DELETED", "Conflicts");
    modification.getSelectionModel().selectFirst();
    final var editActions = actions();
    sourceFilter.setId("recipe-source-filter");
    sourceFilter.getItems().addAll("All", "Static", "Runtime", "Project", "Effective");
    sourceFilter.setValue("All");
    final var filters=new FlowPane(search,modMode,mod,type,modification,language,reset);filters.getStyleClass().add("filter-row");
    final var header = new VBox(new HBox(10, new Label("Source:"), sourceFilter), editActions, tracking, scope, filters);
    header.getStyleClass().add("view-header");
    setTop(header);
    table.setId("recipe-table");table.getColumns().add(resultColumn());
    table.getColumns().add(column("Type", r -> display(r).type(), 125));
    table.getColumns().add(column("Status", this::parseStatus, 90));
    table.getColumns().add(column("Change", r -> editor.hasConflict(r.id()) ? "SOURCE CONFLICT" : editor.status(r.id()).name(), 105));
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    final var sorted = new javafx.collections.transformation.SortedList<>(filtered);
    sorted.comparatorProperty().bind(table.comparatorProperty());
    table.setItems(sorted);
    table.setPlaceholder(new EmptyStateView("No matching recipes","Clear the search or adjust the filters. A static index may not contain recipes created by scripts.","Show all recipes",()->{search.clear();mod.getSelectionModel().selectFirst();type.getSelectionModel().selectFirst();modification.getSelectionModel().selectFirst();all();}));
    table.setRowFactory(view -> {
      final var row = new TableRow<RecipeDefinition>();
      row.setOnMouseClicked(e -> {
        if (e.getClickCount() == 2 && !row.isEmpty()) edit(row.getItem());
      });
      return row;
    });
    table.setContextMenu(contextMenu());
    table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
      if (restoringSelection) return;
      if (staticDraft != null && b != null && a != b) {
        if (!staticDraft.allowClose()) {
          restoringSelection = true;
          try { table.getSelectionModel().select(a); }
          finally { restoringSelection = false; }
          return;
        }
        closeStaticDraft();
      }
      if(b!=null)selectionAnchor=b.id();
      if(b!=null)detailTabs.getSelectionModel().selectFirst();
      select(b);
    });
    viewer.setPadding(new Insets(16));
    final var scroll = new ScrollPane(viewer);
    scroll.setFitToWidth(true);
    final var copyJson = new Button("Copy JSON");
    copyJson.setOnAction(event -> copy(json.getSelectedText().isEmpty() ? json.getText() : json.getSelectedText()));
    copyJson.disableProperty().bind(json.textProperty().isEmpty());
    jsonVersion.valueProperty().addListener((o,a,b)->{
      json.setText(b==null?"":pretty(jsonPayloads.get(b)));
      json.positionCaret(0);
    });
    final var tabs = new TabPane(tab("Recipe", scroll), tab("Raw JSON", new BorderPane(json,new HBox(8,jsonVersion,copyJson),null,null,null)),
        tab("Source", source), tab("Diagnostics", diagnostics));
    tabs.setId("recipe-details");
    detailTabs = tabs;
    split = new SplitPane(table, tabs);
    split.setDividerPositions(0.48);
    setCenter(split);
    search.textProperty().addListener((o, a, b) -> filter());
    mod.valueProperty().addListener((o, a, b) -> filter());
    type.valueProperty().addListener((o, a, b) -> filter());
    modMode.valueProperty().addListener((o, a, b) -> {
      updateModOptions();
      filter();
    });
    modification.valueProperty().addListener((o, a, b) -> filter());
    sourceFilter.valueProperty().addListener((o, a, b) -> {
      if(restoringSelection || b==null) return;
      if(!allowLeaveDraft()) {
        restoringSelection=true;
        try { sourceFilter.setValue(a); } finally { restoringSelection=false; }
        return;
      }
      closeStaticDraft();
      showSource();
    });
    language.valueProperty().addListener((o,a,b)->{
      if(staticDraft!=null)staticDraft.language(b);
      displayCache.clear();table.refresh();filter();select(table.getSelectionModel().getSelectedItem());
    });
    all();
  }

  void runtime(final RuntimeRecipeCatalog.Catalog catalog,
          final DiscoveryResult unified,
          final List<dev.recipeeditor.integrations.gregtech.GregTechMachineCatalog.Machine> machines) {
    closeStaticDraft();
    runtime = catalog;
    editorDiscovery = unified;
    displayNames = new RecipeDisplayNameResolver(unified.registry(),typeNames,machines.stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(machine->machine.id().toString(),machine->machine.label())));
    displayCache.clear();
    this.machines = List.copyOf(machines);
    runtimeIds = catalog.evidence().keySet();
    showSource();
  }

  boolean allowLeaveDraft() {
    return staticDraft == null || staticDraft.allowClose();
  }

  boolean showRuntime() {
    if(runtime==null)return false;
    sourceFilter.setValue("Runtime");
    return sourceFilter.getValue().equals("Runtime");
  }

  private void closeStaticDraft() {
    if (staticDraft == null) return;
    staticDraft.close();
    staticDraft = null;
    split.getItems().set(1, detailTabs);
  }

  private void showDraft(final RecipeEditSession session, final boolean creating, final Runnable deleteAction) {
    if (closed || !allowLeaveDraft()) return;
    final var next = new RecipeEditorSidebar(editorDiscovery, editor, tasks, session, creating, integration);
    next.machines(machines);
    next.language(language.getValue());
    final var origins=editor.authoringBases().findById(session.id()).stream()
            .flatMap(recipe->recipe.source().sourceModIds().stream()).distinct().sorted().toList();
    next.sourceMod(origins.isEmpty()?"UNKNOWN":String.join(", ",origins));
    if (deleteAction != null) {
      final var remove = new Button("Delete recipe");
      remove.setOnAction(event -> deleteAction.run());
      next.action(remove);
    }
    closeStaticDraft();
    staticDraft = next;
    final var close = new Button("Cancel / Back to recipe");
    close.setOnAction(event -> { if(next.allowClose()) closeStaticDraft(); });
    next.action(close);
    split.getItems().set(1, next);
  }

  private void showSource() {
    restoringSelection=true;
    try {
      rows.setAll(catalogRows(sourceFilter.getValue()));
      updateModOptions();
      final String selectedType=type.getValue();
      final var options=new ArrayList<String>();
      options.add("All types");
      rows.stream().map(r->r.type().displayId()).distinct().sorted().forEach(options::add);
      type.getItems().setAll(options);
      type.setValue(options.contains(selectedType)?selectedType:"All types");
    } finally { restoringSelection=false; }
    filter();
  }

  private List<RecipeDefinition> catalogRows(final String mode) {
    if(mode.equals("Static")) return editor.originals().recipes();
    if(mode.equals("Runtime")&&runtime==null) return List.of();
    final var changes=editor.snapshot().changes();
    if(mode.equals("Project")) return changes.keySet().stream().flatMap(id->editor.displayed(id).stream()).toList();
    final var values=new ArrayList<>(editor.effective().recipes());
    if(mode.equals("Runtime")) values.removeIf(recipe->!runtimeIds.contains(recipe.id()));
    if(!mode.equals("Effective")) changes.values().stream().filter(change->change.type()==RecipeChangeType.DELETE)
        .filter(change->!mode.equals("Runtime")||runtimeIds.contains(change.id()))
        .forEach(change->values.addAll(editor.displayed(change.id())));
    return values;
  }

  public void all() {
    scopeItem = null;
    scope.setText("All recipe definitions; imported conflicting sources are retained");
    filter();
    updateTracking();
  }

  public void forItem(final ResourceLocation item, final boolean uses) {
    forResource(dev.recipeeditor.registry.ResourceKind.ITEM, item, uses);
  }

  public void forResource(final dev.recipeeditor.registry.ResourceKind kind, final ResourceLocation item, final boolean uses) {
    scopeKind = kind;
    sourceFilter.setValue("All");
    scopeItem = item;
    scopeUses = uses;
    search.clear();
    mod.getSelectionModel().selectFirst();
    type.getSelectionModel().selectFirst();
    scope.setText((uses ? "Effective uses (possible tag membership): " : "Effective declared outputs: ") + item);
    filter();
  }

  private void filter() {
    if(restoringSelection)return;
    final long revision=++filterRevision;
    if(pendingFilter!=null)pendingFilter.cancel(true);
    final var state=new RecipeFilterState(search.getText(),modMode.getValue(),value(mod),value(type),value(modification),language.getValue());
    final var item=scopeItem;
    final var kind=scopeKind;
    final boolean uses=scopeUses;
    final var evidence=runtime==null?Map.<ResourceLocation,dev.recipeeditor.companionclient.RecipeMergeService.Entry>of():runtime.evidence();
    final var names=displayNames;
    final var candidates=List.copyOf(rows);
    final Runnable evaluate=()->{
      final boolean indexed=!Platform.isFxApplicationThread();
      if(indexed && (indexedNames!=names||!Objects.equals(indexedLanguage,state.language()))) {
        searchIndex.clear();indexedNames=names;indexedLanguage=state.language();
      }
      final var scopeIds = item == null ? Set.<ResourceLocation>of() : (uses ? editor.effective().findRecipesUsing(kind, item) : editor.effective().findRecipesProducing(kind, item))
          .stream().map(RecipeDefinition::id).collect(java.util.stream.Collectors.toSet());
      final var accepted=Collections.newSetFromMap(new IdentityHashMap<RecipeDefinition,Boolean>());
      for(final var recipe:candidates) {
        if(Thread.currentThread().isInterrupted())return;
        final var observation=evidence.get(recipe.id());
        if((item==null||scopeIds.contains(recipe.id())) && (state.type().isEmpty()||recipe.type().displayId().equals(state.type()))
            && (state.mod().isEmpty()||matchesModFilter(recipe,state,observation))
            && (state.change().isEmpty() || (state.change().equals("Conflicts") ? editor.hasConflict(recipe.id()) : editor.status(recipe.id()).name().equals(state.change())))
            && (state.query().isEmpty()||searchTerms(recipe,names,state.language(),observation,indexed).contains(state.query()))) accepted.add(recipe);
      }
      final Runnable apply=()->{
        if(closed||filterRevision!=revision)return;
        final var previous=table.getSelectionModel().getSelectedItem();
        restoringSelection=true;
        try {
          filtered.setPredicate(accepted::contains);
          if(previous!=null && accepted.contains(previous)) table.getSelectionModel().select(previous);
          else {
            final var retained=table.getItems().stream().filter(row->row.id().equals(selectionAnchor)).findFirst();
            if(retained.isPresent())table.getSelectionModel().select(retained.get());
            else if(!table.getItems().isEmpty() && staticDraft==null) table.getSelectionModel().selectFirst();
          }
        } finally { restoringSelection=false; }
        select(table.getSelectionModel().getSelectedItem());
        updateButtons();
      };
      if(Platform.isFxApplicationThread())apply.run();else Platform.runLater(apply);
    };
    if(candidates.size()>2000) pendingFilter=filterWorker.submit(evaluate); else evaluate.run();
  }

  private String value(final ComboBox<String> box) { return box.getSelectionModel().getSelectedIndex()<=0?"":box.getValue(); }

  private void updateModOptions() {
    final String selected=mod.getValue();
    mod.getItems().clear();
    mod.getItems().add("All");
    if (modMode.getValue() == RecipeFilterMode.RECIPE_NAMESPACE)
      rows.stream().map(r -> r.id().namespace()).distinct().sorted().forEach(mod.getItems()::add);
    else {
      mod.getItems().add("UNKNOWN");
      java.util.stream.Stream.concat(editor.originals().recipes().stream(), rows.stream()).flatMap(r -> r.source().sourceModIds().stream()).distinct().sorted().forEach(mod.getItems()::add);
    }
    if(mod.getItems().contains(selected))mod.setValue(selected);else mod.getSelectionModel().selectFirst();
  }

  private boolean matchesModFilter(final RecipeDefinition recipe, final RecipeFilterState state,
          final dev.recipeeditor.companionclient.RecipeMergeService.Entry observed) {
    if(state.modMode()==RecipeFilterMode.RECIPE_NAMESPACE)return recipe.id().namespace().equals(state.mod());
    final var ids=sourceMods(recipe,observed);
    return ids.contains(state.mod()) || (state.mod().equals("UNKNOWN")&&ids.isEmpty());
  }

  private Set<String> sourceMods(final RecipeDefinition recipe,final dev.recipeeditor.companionclient.RecipeMergeService.Entry observed) {
    if(recipe.source().type()==SourceType.RUNTIME)return Set.of();
    if(observed!=null && recipe.source().internalPath().startsWith("changes/"))return observed.provenance().sourceModIds();
    if(!recipe.source().internalPath().startsWith("changes/"))return Set.copyOf(recipe.source().sourceModIds());
    return editor.originals().provenance(recipe.id()).candidateSourceModIds();
  }

  private void refresh(final ResourceLocation id) {
    if (closed) return;
    final String mode=sourceFilter.getValue();
    restoringSelection=true;
    try {
      rows.removeIf(recipe -> {if(!recipe.id().equals(id))return false;displayCache.remove(recipe);return true;});
      if(mode.equals("Static")) rows.addAll(editor.originals().findById(id));
      else if(mode.equals("Effective")) rows.addAll(editor.effective().findById(id));
      else if((!mode.equals("Runtime")||runtimeIds.contains(id))
          && (!mode.equals("Project")||editor.status(id)!=RecipeModificationStatus.UNCHANGED)) rows.addAll(editor.displayed(id));
    } finally { restoringSelection=false; }
    editor.displayed(id).forEach(recipe -> {
      final String value = recipe.type().displayId();
      if (!type.getItems().contains(value)) type.getItems().add(value);
    });
    filter();
    table.refresh();
    updateTracking();
  }

  public void updateTracking() {
    final var values = editor.snapshot().changes().values();
    tracking.setText(values.stream().filter(c -> c.type() == RecipeChangeType.UPDATE).count() + " Modified | " + values.stream().filter(c -> c.type() == RecipeChangeType.CREATE).count() + " Created | " + values.stream().filter(c -> c.type() == RecipeChangeType.DELETE).count() + " Deleted | " + editor.conflicts().size() + " source conflicts | " + (editor.dirty() ? "Project changes not saved to disk" : "Project matches saved snapshot") + " | Export uses saved project changes");
    if (historyButtons.size() == 2) {
      availability(historyButtons.get(0),editor.canUndo(),"No project change to undo");
      availability(historyButtons.get(1),editor.canRedo(),"No project change to redo");
    }
    updateButtons();
  }

  private ToolBar actions() {
    final var create = new Button("+ Create");
    create.setOnAction(e -> create());
    editButton.setOnAction(e -> selected().ifPresent(this::edit));
    duplicateButton.setOnAction(e -> selected().ifPresent(this::duplicate));
    deleteButton.setOnAction(e -> selected().ifPresent(this::delete));
    resetButton.setOnAction(e -> selected().ifPresent(this::reset));
    table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->updateButtons());
    final var undo = new Button("Undo");
    undo.setOnAction(e -> perform(() -> editor.undo()));
    final var redo = new Button("Redo");
    redo.setOnAction(e -> perform(() -> editor.redo()));
    historyButtons.addAll(List.of(undo, redo));
    addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getTarget() instanceof TextInputControl || !event.isControlDown()) return;
      if (event.getCode() == KeyCode.Z) {
        perform(event.isShiftDown() ? () -> editor.redo() : () -> editor.undo());
        event.consume();
      } else if (event.getCode() == KeyCode.Y) {
        perform(() -> editor.redo());
        event.consume();
      }
    });
    final var conflicts = new Button("Source conflicts");
    conflicts.setOnAction(e -> {
      final var ids = editor.conflicts().stream().map(SourceConflict::recipeId).toList();
      if (ids.isEmpty()) {
        new UiTheme().apply(new Alert(Alert.AlertType.INFORMATION, "No blocked source changes", ButtonType.OK)).showAndWait();
        return;
      }
      final var choice = new ChoiceDialog<ResourceLocation>(ids.getFirst(), ids);
      choice.setTitle("Blocked source changes");
      choice.setHeaderText("Review a saved change whose original source changed/disappeared");
      choice.showAndWait().ifPresent(this::reviewConflict);
    });
    return new ToolBar(create, editButton, duplicateButton, deleteButton, resetButton, new Separator(), undo, redo, conflicts);
  }

  private void updateButtons() {
    final var recipe=table.getSelectionModel().getSelectedItem();
    final boolean edit=recipe!=null&&canEdit(recipe);
    final String reason=recipe==null?"Select a recipe":"No verified editable base, or this change is blocked, deleted or linked";
    availability(editButton,edit,reason);
    availability(duplicateButton,recipe!=null&&canDuplicate(recipe),"Select a recipe with a verified copy adapter");
    availability(deleteButton,edit&&canDelete(recipe),edit?"No verified removal strategy for this instance":reason);
    availability(resetButton,recipe!=null&&editor.canReset(recipe.id()),"No independent change to reset");
  }

  private void availability(final Button button,final boolean enabled,final String reason) {
    button.setDisable(!enabled);
    button.setTooltip(enabled?null:new Tooltip(reason));
    button.setAccessibleHelp(enabled?null:reason);
  }

  private Optional<RecipeDefinition> selected() {
    return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
  }

  private void perform(final Runnable work) {
    if(!allowLeaveDraft())return;
    closeStaticDraft();
    tasks.submit(() -> {
      work.run();
      return true;
    }, done -> updateTracking(), this::error);
  }

  private void error(final Throwable error) {
    if (!closed) new UiTheme().apply(new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK)).showAndWait();
  }

  private Optional<ResourceLocation> newId(final String title) {
    final var dialog = new TextInputDialog();
    dialog.initOwner(getScene().getWindow());
    new UiTheme().apply(dialog.getDialogPane());
    dialog.setTitle(title);
    dialog.setHeaderText("Unique Recipe ID: namespace:path");
    final var error = new Label();
    final var content = (javafx.scene.layout.GridPane) dialog.getDialogPane().getContent();
    content.add(error, 0, 2, 2, 1);
    dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
      try {
        final var id = ResourceLocation.parse(dialog.getEditor().getText());
        if (runtimeIds.contains(id) || editor.hasConflict(id) || !editor.originals().findById(id).isEmpty() || !editor.effective().findById(id).isEmpty())
          throw new IllegalArgumentException("ID already exists");
      } catch (RuntimeException invalid) {
        error.setText(invalid.getMessage());
        event.consume();
      }
    });
    return dialog.showAndWait().map(ResourceLocation::parse);
  }

  private void create() {
    final var types = new ComboBox<String>();
    types.getItems().addAll(new RecipeFormService().types());
    if(new dev.recipeeditor.integrations.gregtech.GregTechIntegration().capabilities(integration).enabled("Visual Editing")
        && "2001.6.4-build.120".equals(integration.mods().get("kubejs"))) {
      final var exporter=new dev.recipeeditor.integrations.gregtech.GregTechRecipeExporter();
      machines.stream().filter(machine->!machine.limits().isEmpty()&&exporter.supportsMachine(machine.id().toString()))
          .map(machine->machine.id().toString()).forEach(types.getItems()::add);
    }
    types.getSelectionModel().selectFirst();
    final var dialog = new Dialog<String>();
    dialog.initOwner(getScene().getWindow());
    new UiTheme().apply(dialog.getDialogPane());
    dialog.setTitle("Recipe type");
    dialog.getDialogPane().setContent(new VBox(10, new Label("Choose a type with a registered editor"), types));
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    dialog.setResultConverter(button -> button == ButtonType.OK ? types.getValue() : null);
    dialog.showAndWait().ifPresent(this::createFromRuntime);
  }

  void createFromRuntime(final String serializer) {
    if (!new RecipeFormService().types().contains(serializer) &&
        machines.stream().noneMatch(machine->machine.id().toString().equals(serializer))) return;
    final String hint=serializer.startsWith("gtceu:")?" · ID namespace:"+ResourceLocation.parse(serializer).path()+"/name":"";
    newId("Create Recipe · "+serializer+hint).ifPresent(id -> openNewRecipe(id,serializer));
  }

  private void openNewRecipe(final ResourceLocation id, final String type) {
    final var source = new RecipeSource(SourceType.LOCAL_RESOURCE, List.of(), java.nio.file.Path.of("unsaved-project.json"),
            "changes/" + id, "Unsaved project overlay (logical path)");
    tasks.submit(() -> type.startsWith("gtceu:") ? "{\"type\":\""+type+"\",\"duration\":1,\"inputs\":{},\"outputs\":{}}" : new RecipeFormService().template(type),
            raw -> showDraft(new RecipeEditSession(id, source, raw), true, null), this::error);
  }

  private boolean readOnlyModRecipe(final RecipeDefinition recipe) {
    if(recipe.type().displayId().startsWith("gtceu:") &&
        !new dev.recipeeditor.integrations.gregtech.GregTechIntegration().capabilities(integration).enabled("Visual Editing"))return true;
    return recipe.type().location().flatMap(moddedAdapters::find).map(adapter->!adapter.capabilities(recipe).supports(dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_EDIT)).orElse(false);
  }

  private boolean canEdit(final RecipeDefinition recipe) {
    return editor.canChange(recipe.id()) && !readOnlyModRecipe(recipe);
  }

  private boolean canDuplicate(final RecipeDefinition recipe) {
    if(readOnlyModRecipe(recipe)||editor.hasConflict(recipe.id())||editor.status(recipe.id())==RecipeModificationStatus.DELETED
        ||recipe.status()==RecipeParseStatus.INVALID)return false;
    return recipe.source().type()!=SourceType.RUNTIME || new RuntimeRecipeBase().matches(recipe.source());
  }

  private boolean canDelete(final RecipeDefinition recipe) {
    if(!canEdit(recipe))return false;
    final boolean runtimeBase=editor.authoringBases().findById(recipe.id()).stream().anyMatch(base->base.source().type()==SourceType.RUNTIME);
    if(!runtimeBase)return true;
    final String kubejs=integration.mods().getOrDefault("kubejs","");
    return recipe.type().displayId().startsWith("gtceu:")?kubejs.equals("2001.6.4-build.120"):
        Set.of("2001.6.4-build.120","2001.6.5-build.26").contains(kubejs);
  }

  private void edit(final RecipeDefinition recipe) {
    if(!canEdit(recipe))return;
    final var active=editor.effective().findById(recipe.id());
    final var editable=active.stream().filter(definition->definition.equals(recipe)).findFirst().orElse(active.getFirst());
    if (editor.hasConflict(recipe.id())) {
      reviewConflict(recipe.id());
      return;
    }
    if (editor.status(recipe.id()) == RecipeModificationStatus.DELETED) {
      error(new IllegalStateException("Recipe is deleted. Reset Changes restores it."));
      return;
    }
    final var bases = editor.authoringBases().findById(recipe.id());
    if (bases.size() > 1 && new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION, "This ID has " + bases.size() + " original sources. Saving creates one project override for all candidates; the runtime winner is unknown. Continue?", ButtonType.YES, ButtonType.NO)).showAndWait().orElse(ButtonType.NO) != ButtonType.YES)
      return;
    final String baseline = bases.isEmpty() ? editable.rawJson() : bases.stream().filter(base -> base.source().equals(editable.source())).findFirst().orElse(bases.getFirst()).rawJson();
    final var source = new RecipeSource(SourceType.LOCAL_RESOURCE, List.of(), java.nio.file.Path.of("unsaved-project.json"), "changes/" + recipe.id(), "Unsaved project overlay (logical path)");
    showDraft(new RecipeEditSession(recipe.id(), source, editable.rawJson(), baseline), false, canDelete(recipe)?()->delete(recipe):null);
  }

  private void duplicate(final RecipeDefinition recipe) {
    if(!canDuplicate(recipe))return;
    newId("Duplicate Recipe").ifPresent(id -> {
      final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),java.nio.file.Path.of("unsaved-project.json"),
          "changes/"+id,"Project copy of "+recipe.id());
      showDraft(new RecipeEditSession(id,source,recipe.rawJson()),true,null);
    });
  }

  private void delete(final RecipeDefinition recipe) {
    if(!canDelete(recipe))return;
    if (new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION, "Logically delete " + recipe.id() + " from the project? Original JAR files will not change.", ButtonType.YES, ButtonType.NO)).showAndWait().orElse(ButtonType.NO) == ButtonType.YES)
      perform(() -> editor.delete(recipe.id()));
  }

  private void reset(final RecipeDefinition recipe) {
    if(!editor.canReset(recipe.id()))return;
    if (editor.hasConflict(recipe.id())) reviewConflict(recipe.id());
    else perform(() -> editor.reset(recipe.id()));
  }

  private void reviewConflict(final ResourceLocation id) {
    final var conflict = editor.conflicts().stream().filter(c -> c.recipeId().equals(id)).findFirst().orElseThrow();
    final var area = area();
    area.setText(conflict.message() + "\nSaved bases: " + conflict.savedChange().originals() + "\nCurrent bases: " + conflict.currentSources() + "\nSaved replacement JSON:\n" + conflict.savedChange().replacement().stream().map(RecipeDefinition::rawJson).collect(java.util.stream.Collectors.joining("\n")));
    final var dialog = new Dialog<ButtonType>();
    dialog.setTitle("Source conflict - automatic application blocked");
    dialog.initOwner(getScene().getWindow());
    dialog.getDialogPane().setContent(area);
    dialog.getDialogPane().setPrefSize(850, 600);
    final var discard = new ButtonType("Discard saved change", ButtonBar.ButtonData.OTHER);
    dialog.getDialogPane().getButtonTypes().addAll(discard, ButtonType.CLOSE);
    dialog.setResultConverter(button -> button);
    if (dialog.showAndWait().orElse(ButtonType.CLOSE) == discard && new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION, "Discard this blocked saved change? Copy any JSON you need first.", ButtonType.YES, ButtonType.NO)).showAndWait().orElse(ButtonType.NO) == ButtonType.YES)
      perform(() -> editor.discardConflict(id));
  }

  private ContextMenu contextMenu() {
    final var menu = new ContextMenu();
    final var export = new Menu("Export Recipe");
    for (final var format : dev.recipeeditor.exporters.ExportFormat.values())
      export.getItems().add(item(format.name(), recipe -> exportRecipe.accept(recipe, format)));
    menu.getItems().add(export);
    final MenuItem open = item("Open Recipe", recipe -> select(recipe)), edit = item("Edit Recipe", this::edit), duplicate = item("Duplicate Recipe", this::duplicate), delete = item("Delete Recipe", this::delete), reset = item("Reset Changes", this::reset);
    menu.getItems().addAll(open, edit, duplicate, delete, reset, item("Copy Recipe ID", r -> copy(r.id().toString())), item("Copy Recipe JSON", r -> copy(r.rawJson())), item("Show Source", r -> {
      select(r);
      detailTabs.getSelectionModel().select(2);
    }));
    menu.setOnShowing(event -> {
      final var chosen = selected();
      final boolean unavailable = chosen.isEmpty();
      menu.getItems().forEach(action -> action.setDisable(unavailable));
      if (chosen.isPresent()) {
        final var id = chosen.get().id();
        duplicate.setDisable(!canDuplicate(chosen.get()));
        delete.setDisable(!canDelete(chosen.get()));
        edit.setDisable(!canEdit(chosen.get()));
        export.setDisable(readOnlyModRecipe(chosen.get()) || editor.hasConflict(id)
            || chosen.get().source().type()==SourceType.RUNTIME && !editor.snapshot().changes().containsKey(id));
        edit.setText(editor.status(id) == RecipeModificationStatus.DELETED ? "Edit Recipe (Reset Changes restores deleted recipe)" : "Edit Recipe");
        reset.setDisable(!editor.canReset(id));
        reset.setText(editor.status(id) == RecipeModificationStatus.UNCHANGED && !editor.hasConflict(id) ? "Reset Changes (no saved project change)" : "Reset Changes");
      }
    });
    return menu;
  }

  private MenuItem item(final String title, final java.util.function.Consumer<RecipeDefinition> action) {
    final var item = new MenuItem(title);
    item.setOnAction(e -> selected().ifPresent(action));
    return item;
  }

  private void copy(final String value) {
    final var content = new ClipboardContent();
    content.putString(value);
    Clipboard.getSystemClipboard().setContent(content);
  }

  @Override
  public void close() {
    closeStaticDraft();
    closed = true;
    filterWorker.shutdownNow();
    textures.close();displayCache.clear();
    editor.removeListener(listener);
  }

  private RecipeDisplayNameResolver.Display display(final RecipeDefinition recipe){return displayCache.computeIfAbsent(recipe,r->displayNames.resolve(r,language.getValue()));}

  private String parseStatus(final RecipeDefinition recipe) {
    final var observed=observation(recipe);
    return observed!=null && (recipe.source().type()==SourceType.RUNTIME||sourceFilter.getValue().equals("Runtime"))
        ?observed.runtime().status().name():recipe.status().name();
  }

  private dev.recipeeditor.companionclient.RecipeMergeService.Entry observation(final RecipeDefinition recipe) {
    return runtime==null?null:runtime.evidence().get(recipe.id());
  }

  private String searchTerms(final RecipeDefinition recipe,final RecipeDisplayNameResolver names,final String locale,
          final dev.recipeeditor.companionclient.RecipeMergeService.Entry observed,final boolean indexed) {
    if(indexed) {
      final var cached=searchIndex.get(recipe);
      if(cached!=null)return cached;
      if(searchIndex.size()>=150000)searchIndex.clear();
    }
    final var value=names.resolve(recipe,locale);
    final var tokens=new ArrayList<>(List.of(recipe.id().toString(),recipe.type().displayId(),value.name(),value.type(),value.machine()));
    if(observed!=null)tokens.addAll(List.of(observed.runtime().serializer(),observed.runtime().type()));
    tokens.addAll(sourceMods(recipe,observed));
    recipe.inputs().forEach(input->tokens.add(describe(input)));
    for(final var output:recipe.outputs()) {
      if(output instanceof RecipeOutput.Fixed fixed)tokens.add(fixed.id().toString());
      if(output instanceof RecipeOutput.Capability capability)tokens.add(describe(capability.ingredient()));
    }
    final var terms=String.join("\n",tokens).toLowerCase(Locale.ROOT);
    if(indexed)searchIndex.put(recipe,terms);
    return terms;
  }

  private TableColumn<RecipeDefinition,RecipeDefinition> resultColumn() {
    final var column=new TableColumn<RecipeDefinition,RecipeDefinition>("Result");column.setPrefWidth(280);
    column.setCellValueFactory(cell->new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue()));
    column.setComparator(Comparator.comparing(recipe->display(recipe).name(),String.CASE_INSENSITIVE_ORDER));
    column.setCellFactory(view->new TableCell<>() {
      @Override protected void updateItem(final RecipeDefinition recipe,final boolean empty) {
        super.updateItem(recipe,empty);setText(null);setGraphic(null);setTooltip(null);
        if(empty||recipe==null)return;
        final var value=display(recipe);final var title=new Label(value.name()+(value.additionalOutputs()>0?" +"+value.additionalOutputs():""));title.getStyleClass().add("recipe-list-name");
        final var id=new Label(recipe.id().toString());id.getStyleClass().add("muted");id.setMaxWidth(240);id.setTextOverrun(OverrunStyle.ELLIPSIS);
        final var names=new VBox(4,title,id);final var row=new HBox(10,names);row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        value.previewOutput().flatMap(output->editorDiscovery.registry().find(output.id()).stream().filter(entry->entry.kind()==output.kind()).findFirst()).ifPresent(entry->row.getChildren().addFirst(new ResourcePreview(entry,editorDiscovery,textures,36,()->getItem()==recipe,preview->{})));
        setGraphic(row);setTooltip(new Tooltip(value.name()+"\nRecipe ID: "+recipe.id()+"\nSerializer: "+recipe.type().displayId()+"\nMachine: "+value.machine()+(value.additionalOutputs()>0?"\nRepresentative output; additional outputs: "+value.additionalOutputs():"")));
      }
    });return column;
  }

  private String changePrefix(final ResourceLocation id) {
    if (editor.hasConflict(id)) return "[!] ";
    return switch (editor.status(id)) {
      case CREATED -> "[+] ";
      case MODIFIED -> "[M] ";
      case DELETED -> "[-] ";
      case UNCHANGED -> "";
    };
  }

  private TableColumn<RecipeDefinition, String> column(final String title, final java.util.function.Function<RecipeDefinition, String> value, final int width) {
    final var column = new TableColumn<RecipeDefinition, String>(title);
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    column.setPrefWidth(width);
    return column;
  }

  private TextArea area() {
    final var area = new TextArea();
    area.setEditable(false);
    area.setWrapText(true);
    return area;
  }

  private Tab tab(final String title, final javafx.scene.Node content) {
    final var tab = new Tab(title, content);
    tab.setClosable(false);
    return tab;
  }

  private Label label(final String text) {
    final var label = new Label(text);
    label.setWrapText(true);
    return label;
  }

  private void select(final RecipeDefinition recipe) {
    viewer.getChildren().clear();
    json.clear();
    jsonVersion.getItems().clear();
    jsonPayloads.clear();
    source.clear();
    diagnostics.clear();
    if (recipe == null) {
      viewer.getChildren().add(new EmptyStateView("No recipe selected","Select a recipe to inspect its definition, source evidence and diagnostics."));
      return;
    }
    final var presentation=display(recipe);
    final var observed=observation(recipe);
    viewer.getChildren().addAll(label(presentation.name()),label("Type: "+presentation.type()+" · "+presentation.machine()),label("Recipe ID: "+recipe.id()),label("Recipe status: "+editor.status(recipe.id())+(editor.hasConflict(recipe.id())?" · SOURCE CONFLICT":"")));
    presentation.previewOutput().flatMap(output->editorDiscovery.registry().find(output.id()).stream().filter(entry->entry.kind()==output.kind()).findFirst())
        .ifPresent(entry->viewer.getChildren().addFirst(new ResourcePreview(entry,editorDiscovery,textures,48,
            ()->table.getSelectionModel().getSelectedItem()==recipe,preview->{})));
    if (recipe.status() == RecipeParseStatus.UNSUPPORTED || recipe.status() == RecipeParseStatus.INVALID)
      viewer.getChildren().add(label("Semantic view unavailable. See Raw JSON and Diagnostics."));
    else {
      final var layout=new RecipeLayoutResolver().resolve(recipe);
      if(layout.isPresent()) {
        final var drawing=new VBox();
        final var scale=new Spinner<Integer>(40,80,64,8);scale.setPrefWidth(100);scale.setAccessibleText("Recipe slot size in pixels");
        final Runnable render=()->{final var view=new RecipeLayoutRenderer(layout.get(),editorDiscovery,textures,scale.getValue(),language.getValue(),RecipeLayoutRenderer.Mode.VIEWER,null);view.resourceNavigation(resourceNavigation);drawing.getChildren().setAll(view);};
        scale.valueProperty().addListener((o,a,b)->render.run());render.run();
        viewer.getChildren().addAll(drawing,new HBox(8,new Label("Slot size"),scale));
      } else {
        viewer.getChildren().add(label("No verified visual layout. Inspect Raw JSON for inputs and outputs."));
        recipe.properties().forEach(property->viewer.getChildren().add(label(property.name()+": "+property.rawValue())));
      }
    }
    final var machine=machines.stream().filter(row->row.id().toString().equals(recipe.type().displayId())).findFirst();
    machine.ifPresent(row->viewer.getChildren().add(new GregTechMachineSelector(machines,row.id(),ignored->{},false)));
    final var editAction=new Button("Edit recipe");availability(editAction,canEdit(recipe),"No verified editable base");
    editAction.setOnAction(event->edit(recipe));
    final var copyAction=new Button("Duplicate");availability(copyAction,canDuplicate(recipe),"No verified copy adapter");
    copyAction.setOnAction(event->duplicate(recipe));
    final var deleteAction=new Button("Delete");availability(deleteAction,canDelete(recipe),"No verified removal strategy");
    deleteAction.setOnAction(event->delete(recipe));
    viewer.getChildren().add(new HBox(8,editAction,copyAction,deleteAction));
    final var currentChange = editor.persistentSnapshot().changes().get(recipe.id());
    if(recipe.source().internalPath().startsWith("changes/"))
      jsonPayloads.put("Current project JSON",recipe.rawJson());
    if(observed!=null) {
      jsonPayloads.put("Normalized runtime JSON",observed.runtime().properties().toString());
      for(int i=0;i<observed.staticDefinitions().size();i++)
        jsonPayloads.put("Original static JSON · candidate "+(i+1),observed.staticDefinitions().get(i).rawJson());
    } else if(!recipe.source().internalPath().startsWith("changes/"))jsonPayloads.put("Original JSON",recipe.rawJson());
    if(currentChange!=null&&currentChange.metadata().containsKey("mre.runtime.originalSerializerJson"))
      jsonPayloads.put("Original verified serializer projection",currentChange.metadata().get("mre.runtime.originalSerializerJson"));
    jsonVersion.getItems().setAll(jsonPayloads.keySet());
    jsonVersion.getSelectionModel().selectFirst();
    final var provenance = currentChange == null ? editor.originals().provenance(recipe.id()) : new dev.recipeeditor.exporters.ExportSourceMapping().resolve(currentChange, editor.authoringBases(), projectFile, editor.hasConflict(recipe.id()));
    source.setText((observed==null?provenance.describe():"Minecraft: "+runtime.minecraftVersion()+"\n"+observed.provenance().describe()
        +(currentChange==null?"":"\nProject overlay\n"+provenance.describe())));
    final var messages=new ArrayList<String>();
    messages.add("Parsing: "+parseStatus(recipe));
    messages.add("Edit: "+canEdit(recipe)+" · Duplicate: "+canDuplicate(recipe)+" · Delete: "+canDelete(recipe));
    messages.add("Generated export is validated for the chosen format in the Export Wizard.");
    recipe.diagnostics().forEach(diagnostic->messages.add(diagnostic.message()));
    if(observed!=null) {
      messages.addAll(observed.diagnostics());
      messages.addAll(observed.runtime().diagnostics());
    }
    diagnostics.setText(String.join("\n",messages));
  }

  private String pretty(final String raw) {
    if(raw==null)return "";
    try { return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(com.google.gson.JsonParser.parseString(raw)); }
    catch(com.google.gson.JsonParseException invalid) { return raw; }
  }

  private String describe(final IngredientDefinition value) {
    if (value instanceof IngredientDefinition.Item item) return item.id().toString();
    if (value instanceof IngredientDefinition.Tag tag) return "#" + tag.id();
    if (value instanceof IngredientDefinition.Fluid fluid) return fluid.id().toString();
    if (value instanceof IngredientDefinition.Sized sized) return describe(sized.ingredient());
    if (value instanceof IngredientDefinition.Alternatives alternatives)
      return alternatives.choices().stream().map(this::describe).collect(java.util.stream.Collectors.joining(" OR "));
    return "Unsupported ingredient (see JSON)";
  }
}
