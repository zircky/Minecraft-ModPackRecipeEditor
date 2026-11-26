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
  private final DiscoveryResult result;
  private DiscoveryResult editorDiscovery;
  private List<dev.recipeeditor.integrations.gregtech.GregTechMachineCatalog.Machine> machines=List.of();
  private java.util.function.BiConsumer<dev.recipeeditor.registry.ResourceKind,ResourceLocation> resourceNavigation=(kind,id)->{};
  public void resourceNavigation(final java.util.function.BiConsumer<dev.recipeeditor.registry.ResourceKind,ResourceLocation> action){resourceNavigation=Objects.requireNonNull(action);}
  private final dev.recipeeditor.registry.TextureResourceService textures=new dev.recipeeditor.registry.TextureResourceService();
  private final dev.recipeeditor.integrations.api.IntegrationContext integration;
  private final dev.recipeeditor.integrations.api.ModdedRecipeAdapterRegistry moddedAdapters=new dev.recipeeditor.integrations.ProjectRecipeAdapters().registry();
  private final RecipeTypeDisplayNameResolver typeNames=new RecipeTypeDisplayNameResolver();
  private final RecipeDisplayNameResolver displayNames;
  private final Map<RecipeDefinition,RecipeDisplayNameResolver.Display> displayCache=new IdentityHashMap<>();
  private final ComboBox<String> language=new ComboBox<>();
  private Set<ResourceLocation> runtimeIds = Set.of();
  private final TextField search = new TextField();
  private final ComboBox<String> mod = new ComboBox<>(), type = new ComboBox<>();
  private final ComboBox<RecipeFilterMode> modMode = new ComboBox<>(FXCollections.observableArrayList(RecipeFilterMode.values()));
  private final TableView<RecipeDefinition> table = new TableView<>();
  private final VBox viewer = new VBox(10);
  private final TextArea json = area(), source = area(), diagnostics = area();
  private final Label scope = new Label();
  private final RecipeEditorService editor;
  private final EditorTasks tasks;
  private final javafx.collections.ObservableList<RecipeDefinition> rows = FXCollections.observableArrayList();
  private final javafx.collections.transformation.FilteredList<RecipeDefinition> filtered = new javafx.collections.transformation.FilteredList<>(rows);
  private final ComboBox<String> modification = new ComboBox<>();
  private final Label tracking = new Label();
  private final Label provenance = new Label("STATIC / PROJECT RECIPES — runtime state has not been merged");
  private final java.util.function.Consumer<ResourceLocation> listener;
  private final java.util.List<Button> historyButtons = new ArrayList<>();
  private ResourceLocation scopeItem;
  private dev.recipeeditor.registry.ResourceKind scopeKind = dev.recipeeditor.registry.ResourceKind.ITEM;
  private boolean scopeUses, closed;
  private TabPane detailTabs;
  private final ComboBox<String> sourceFilter = new ComboBox<>();
  private javafx.scene.Node staticCenter;
  private RuntimeRecipeBrowserView runtimeView;
  private RecipeEditorSidebar staticDraft;
  private boolean restoringSelection;
  private ToolBar editActions;
  private java.nio.file.Path projectFile;

  public void projectFile(final java.nio.file.Path file) {
    projectFile = file;
  }

  public RecipeBrowserView(final DiscoveryResult result, final RecipeEditorService editor, final EditorTasks tasks, final java.util.function.BiConsumer<RecipeDefinition, dev.recipeeditor.exporters.ExportFormat> exportRecipe, final dev.recipeeditor.integrations.api.IntegrationContext integration) {
    this.integration = integration;
    this.exportRecipe = exportRecipe;
    this.result = result;
    editorDiscovery = result;
    displayNames=new RecipeDisplayNameResolver(result.registry(),typeNames);
    this.editor = editor;
    this.tasks = tasks;
    getStyleClass().add("workspace-view");
    rows.setAll(editor.effective().recipes());
    editor.snapshot().changes().values().stream().filter(change -> change.type() == RecipeChangeType.DELETE).forEach(change -> rows.addAll(editor.originals().findById(change.id())));
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
    editActions = actions();
    sourceFilter.getItems().addAll("All", "Static", "Runtime");
    sourceFilter.setValue("Static");
    final var filters=new FlowPane(search,modMode,mod,type,modification,language,reset);filters.getStyleClass().add("filter-row");
    final var header = new VBox(new HBox(10, new Label("Source:"), sourceFilter), editActions, tracking, provenance, scope, filters);
    header.getStyleClass().add("view-header");
    setTop(header);
    table.setId("recipe-table");table.getColumns().add(resultColumn());
    table.getColumns().add(column("Type", r -> display(r).type(), 125));
    table.getColumns().add(column("Parse", r -> r.status().name(), 90));
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
      select(b);
    });
    viewer.setPadding(new Insets(16));
    final var scroll = new ScrollPane(viewer);
    scroll.setFitToWidth(true);
    final var tabs = new TabPane(tab("Recipe", scroll), tab("Raw JSON", json), tab("Source", source), tab("Diagnostics", diagnostics));
    detailTabs = tabs;
    final var split = new SplitPane(table, tabs);
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
    staticCenter = getCenter();
    sourceFilter.valueProperty().addListener((o, a, b) -> showSource());
    language.valueProperty().addListener((o,a,b)->{displayCache.clear();table.refresh();filter();select(table.getSelectionModel().getSelectedItem());});
    all();
  }

  public void runtime(final dev.recipeeditor.companionclient.RecipeMergeService.Result merge,
          final DiscoveryResult unified,
          final List<dev.recipeeditor.integrations.gregtech.GregTechMachineCatalog.Machine> machines) {
    editorDiscovery = unified;
    this.machines = List.copyOf(machines);
    if (staticCenter == null) staticCenter = getCenter();
    runtimeIds = merge.entries().stream().filter(e -> e.runtime() != null).map(e -> e.id()).collect(java.util.stream.Collectors.toSet());
    if (runtimeView != null) runtimeView.close();
    runtimeView = new RuntimeRecipeBrowserView(merge.entries(), this::copyRuntime, this::canCopyRuntime,
            entry -> editableSource(entry, merge).ifPresent(this::edit),
            entry -> editableSource(entry, merge).isPresent());
    runtimeView.machines(this.machines);
    sourceFilter.setValue("All");
    showSource();
  }

  boolean allowLeaveDraft() {
    return (staticDraft == null || staticDraft.allowClose()) && (runtimeView == null || runtimeView.allowLeave());
  }

  private Optional<RecipeDefinition> editableSource(final dev.recipeeditor.companionclient.RecipeMergeService.Entry row,
          final dev.recipeeditor.companionclient.RecipeMergeService.Result merge) {
    if (row.staticDefinitions().size() != 1 || merge.changedOrMissing().contains(row.id())
            || editor.hasConflict(row.id())) return Optional.empty();
    if (row.runtime()!=null && row.runtime().properties().has("runtimeVariants")) return Optional.empty();
    final var current = editor.effective().findById(row.id());
    return current.size() == 1 && current.getFirst().status() == RecipeParseStatus.PARSED
            && !readOnlyModRecipe(current.getFirst()) ? Optional.of(current.getFirst()) : Optional.empty();
  }

  private void closeStaticDraft() {
    if (staticDraft == null) return;
    staticDraft.close();
    staticDraft = null;
    ((SplitPane)staticCenter).getItems().set(1, detailTabs);
  }

  private void showDraft(final RecipeEditSession session, final boolean creating, final Runnable deleteAction) {
    if (closed || !allowLeaveDraft()) return;
    final var next = new RecipeEditorSidebar(editorDiscovery, editor, tasks, session, creating, integration);
    next.machines(machines);
    final var origins=editor.originals().findById(session.id()).stream()
            .flatMap(recipe->recipe.source().sourceModIds().stream()).distinct().sorted().toList();
    next.sourceMod(origins.isEmpty()?"UNKNOWN":String.join(", ",origins));
    if (deleteAction != null) {
      final var remove = new Button("Delete recipe");
      remove.setOnAction(event -> { if(next.allowClose()) deleteAction.run(); });
      next.action(remove);
    }
    if (runtimeView != null && getCenter() == runtimeView) {
      runtimeView.openDraft(next);
      return;
    }
    closeStaticDraft();
    staticDraft = next;
    final var close = new Button("Close sidebar");
    close.setOnAction(event -> { if(next.allowClose()) closeStaticDraft(); });
    next.action(close);
    ((SplitPane)staticCenter).getItems().set(1, next);
  }

  private void showSource() {
    final boolean original = sourceFilter.getValue().equals("Static") || (runtimeView == null && sourceFilter.getValue().equals("All"));
    editActions.setDisable(!original);
    provenance.setText(original ? "STATIC / PROJECT RECIPES — source files and project overlay" : "RUNTIME SNAPSHOT — actual collected state; normalized properties are read only");
    if (original) {
      setCenter(staticCenter);
      return;
    }
    if (runtimeView == null) {
      setCenter(new Label("No complete runtime snapshot. Use Runtime → Synchronize with Minecraft."));
      return;
    }
    runtimeView.runtimeOnly(sourceFilter.getValue().equals("Runtime"));
    setCenter(runtimeView);
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
    sourceFilter.setValue("Static");
    showSource();
    scopeItem = item;
    scopeUses = uses;
    search.clear();
    mod.getSelectionModel().selectFirst();
    type.getSelectionModel().selectFirst();
    scope.setText((uses ? "Effective uses (possible tag membership): " : "Effective declared outputs: ") + item);
    filter();
  }

  private void filter() {
    final String query = search.getText().toLowerCase(Locale.ROOT).strip();
    final String selectedMod = mod.getSelectionModel().getSelectedIndex() <= 0 ? "" : mod.getValue();
    final String selectedType = type.getSelectionModel().getSelectedIndex() == 0 ? "" : type.getValue();
    final var scopeIds = scopeItem == null ? Set.<ResourceLocation>of() : (scopeUses ? editor.effective().findRecipesUsing(scopeKind, scopeItem) : editor.effective().findRecipesProducing(scopeKind, scopeItem)).stream().map(RecipeDefinition::id).collect(java.util.stream.Collectors.toSet());
    filtered.setPredicate(recipe -> (scopeItem == null || scopeIds.contains(recipe.id())) && (selectedMod.isEmpty() || matchesModFilter(recipe, selectedMod))
        && (selectedType.isEmpty() || recipe.type().displayId().equals(selectedType))
        && matchesSearch(recipe,query)
        && (modification.getSelectionModel().getSelectedIndex() == 0 || (modification.getValue().equals("Conflicts") ? editor.hasConflict(recipe.id()) : editor.status(recipe.id()).name().equals(modification.getValue()))));
    if (!table.getItems().isEmpty() && table.getSelectionModel().getSelectedItem() == null)
      table.getSelectionModel().selectFirst();
  }

  private void updateModOptions() {
    mod.getItems().clear();
    mod.getItems().add("All");
    if (modMode.getValue() == RecipeFilterMode.RECIPE_NAMESPACE)
      rows.stream().map(r -> r.id().namespace()).distinct().sorted().forEach(mod.getItems()::add);
    else {
      mod.getItems().add("UNKNOWN");
      java.util.stream.Stream.concat(editor.originals().recipes().stream(), rows.stream()).flatMap(r -> r.source().sourceModIds().stream()).distinct().sorted().forEach(mod.getItems()::add);
    }
    mod.getSelectionModel().selectFirst();
  }

  private boolean matchesModFilter(final RecipeDefinition recipe, final String value) {
    if (modMode.getValue() == RecipeFilterMode.RECIPE_NAMESPACE)
      return RecipeFilterMode.RECIPE_NAMESPACE.matches(recipe, value);
    if (editor.status(recipe.id()) == RecipeModificationStatus.UNCHANGED || editor.status(recipe.id()) == RecipeModificationStatus.DELETED)
      return RecipeFilterMode.SOURCE_MOD.matches(recipe, value);
    final var sources = new ArrayList<>(editor.originals().findById(recipe.id()));
    sources.add(recipe);
    final var ids = sources.stream().flatMap(r -> r.source().sourceModIds().stream()).collect(java.util.stream.Collectors.toSet());
    return ids.contains(value) || (value.equals("UNKNOWN") && ids.isEmpty());
  }

  private void refresh(final ResourceLocation id) {
    if (closed) return;
    rows.removeIf(recipe -> {if(!recipe.id().equals(id))return false;displayCache.remove(recipe);return true;});
    rows.addAll(editor.displayed(id));
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
      historyButtons.get(0).setDisable(!editor.canUndo());
      historyButtons.get(1).setDisable(!editor.canRedo());
    }
  }

  private ToolBar actions() {
    final var create = new Button("+ Create");
    create.setOnAction(e -> create());
    final var edit = new Button("Edit");
    edit.setOnAction(e -> selected().ifPresent(this::edit));
    final var duplicate = new Button("Duplicate");
    duplicate.setOnAction(e -> selected().ifPresent(this::duplicate));
    final var delete = new Button("Delete");
    delete.setOnAction(e -> selected().ifPresent(this::delete));
    table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{final boolean readOnly=b!=null&&readOnlyModRecipe(b);edit.setDisable(b==null||readOnly);duplicate.setDisable(b==null||readOnly);delete.setDisable(b==null||readOnly);});
    final var reset = new Button("Reset changes");
    reset.setOnAction(e -> selected().ifPresent(this::reset));
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
    return new ToolBar(create, edit, duplicate, delete, reset, new Separator(), undo, redo, conflicts);
  }

  private Optional<RecipeDefinition> selected() {
    return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
  }

  private void perform(final Runnable work) {
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
        if (runtimeIds.contains(id) || !editor.originals().findById(id).isEmpty() || !editor.effective().findById(id).isEmpty())
          throw new IllegalArgumentException("ID already exists");
      } catch (RuntimeException invalid) {
        error.setText(invalid.getMessage());
        event.consume();
      }
    });
    return dialog.showAndWait().map(ResourceLocation::parse);
  }

  private void create() {
    final var id = newId("Create New Recipe");
    if (id.isEmpty()) return;
    final var types = new ComboBox<String>();
    types.getItems().addAll(new RecipeFormService().types());
    types.setEditable(true);
    types.getSelectionModel().selectFirst();
    final var dialog = new Dialog<String>();
    dialog.initOwner(getScene().getWindow());
    new UiTheme().apply(dialog.getDialogPane());
    dialog.setTitle("Recipe type");
    final var error = new Label();
    dialog.getDialogPane().setContent(new VBox(10, new Label("Choose a known type or enter a custom Resource Location"), types, error));
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
      try {
        final var selectedType=ResourceLocation.parse(types.getEditor().getText());
        if(moddedAdapters.supports(selectedType))throw new IllegalArgumentException("This mod adapter is read only; creation is not verified yet");
      } catch (RuntimeException invalid) {
        error.setText(invalid.getMessage());
        event.consume();
      }
    });
    dialog.setResultConverter(button -> button == ButtonType.OK ? types.getEditor().getText() : null);
    dialog.showAndWait().ifPresent(type -> openNewRecipe(id.orElseThrow(),type));
  }

  void createFromRuntime(final String serializer) {
    if (!new RecipeFormService().types().contains(serializer)) return;
    newId("Create Recipe · " + serializer).ifPresent(id -> openNewRecipe(id,serializer));
  }

  private void openNewRecipe(final ResourceLocation id, final String type) {
    final var source = new RecipeSource(SourceType.LOCAL_RESOURCE, List.of(), java.nio.file.Path.of("unsaved-project.json"),
            "changes/" + id, "Unsaved project overlay (logical path)");
    tasks.submit(() -> new RecipeFormService().template(type),
            raw -> showDraft(new RecipeEditSession(id, source, raw), true, null), this::error);
  }

  private void copyRuntime(final dev.recipeeditor.companionprotocol.RuntimeData.Recipe recipe) {
    final var vanilla = new dev.recipeeditor.companionclient.VanillaRuntimeDraft();
    if (vanilla.supports(recipe)) {
      newId("Copy loaded Vanilla recipe to a new ID").ifPresent(id -> {
        final var source = new RecipeSource(SourceType.LOCAL_RESOURCE, List.of(), java.nio.file.Path.of("unsaved-project.json"),
                "changes/" + id, "New project recipe copied from normalized Vanilla state " + recipe.id());
        tasks.submit(() -> vanilla.copy(recipe), raw -> showDraft(new RecipeEditSession(id, source, raw), true, null), this::error);
      });
      return;
    }
    if (!new dev.recipeeditor.integrations.gregtech.GregTechIntegration().capabilities(integration).enabled("Visual Editing")) {
      error(new IllegalArgumentException("GT version is not edit-verified"));
      return;
    }
    newId("Copy loaded GT state: use namespace:" + ResourceLocation.parse(recipe.type()).path() + "/name (new ID)").ifPresent(id -> {
      final var source = new RecipeSource(SourceType.LOCAL_RESOURCE, List.of("gtceu"), java.nio.file.Path.of("unsaved-project.json"), "changes/" + id, "Explicit new-ID copy of normalized runtime state; original source UNKNOWN");
      tasks.submit(() -> new dev.recipeeditor.integrations.gregtech.GregTechRuntimeDraft().copy(recipe),
              raw -> showDraft(new RecipeEditSession(id, source, raw), true, null), this::error);
    });
  }

  private boolean canCopyRuntime(final dev.recipeeditor.companionprotocol.RuntimeData.Recipe recipe) {
    if (new dev.recipeeditor.companionclient.VanillaRuntimeDraft().supports(recipe)) return true;
    if (!recipe.adapter().equals("gtceu-7.1.4")
            || !new dev.recipeeditor.integrations.gregtech.GregTechIntegration().capabilities(integration).enabled("Visual Editing"))
      return false;
    try {
      new dev.recipeeditor.integrations.gregtech.GregTechRuntimeDraft().copy(recipe);
      return true;
    } catch (RuntimeException unsupported) { return false; }
  }

  private String capabilityStatus(final dev.recipeeditor.integrations.api.RecipeTypeCapabilities caps,final dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature feature){return caps.supports(feature)?"SUPPORTED":"UNSUPPORTED";}

  private boolean readOnlyModRecipe(final RecipeDefinition recipe) {
    return recipe.type().location().flatMap(moddedAdapters::find).map(adapter->!adapter.capabilities(recipe).supports(dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_EDIT)).orElse(false);
  }

  private void edit(final RecipeDefinition recipe) {
    if(readOnlyModRecipe(recipe)){error(new IllegalStateException("This mod adapter supports read-only viewing; authoring/export are not yet verified"));return;}
    if (editor.hasConflict(recipe.id())) {
      reviewConflict(recipe.id());
      return;
    }
    if (editor.status(recipe.id()) == RecipeModificationStatus.DELETED) {
      error(new IllegalStateException("Recipe is deleted. Reset Changes restores it."));
      return;
    }
    final var bases = editor.originals().findById(recipe.id());
    if (bases.size() > 1 && new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION, "This ID has " + bases.size() + " original sources. Saving creates one project override for all candidates; the runtime winner is unknown. Continue?", ButtonType.YES, ButtonType.NO)).showAndWait().orElse(ButtonType.NO) != ButtonType.YES)
      return;
    final String baseline = bases.isEmpty() ? recipe.rawJson() : bases.stream().filter(base -> base.source().equals(recipe.source())).findFirst().orElse(bases.getFirst()).rawJson();
    final var source = new RecipeSource(SourceType.LOCAL_RESOURCE, List.of(), java.nio.file.Path.of("unsaved-project.json"), "changes/" + recipe.id(), "Unsaved project overlay (logical path)");
    showDraft(new RecipeEditSession(recipe.id(), source, recipe.rawJson(), baseline), false, () -> delete(recipe));
  }

  private void duplicate(final RecipeDefinition recipe) {
    if(readOnlyModRecipe(recipe)){error(new IllegalStateException("This mod adapter supports read-only viewing; authoring/export are not yet verified"));return;}
    newId("Duplicate Recipe").ifPresent(id -> tasks.submit(() -> editor.duplicate(recipe, id), this::edit, this::error));
  }

  private void delete(final RecipeDefinition recipe) {
    if(readOnlyModRecipe(recipe)){error(new IllegalStateException("This mod adapter supports read-only viewing; authoring/export are not yet verified"));return;}
    if (new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION, "Logically delete " + recipe.id() + " from the project? Original JAR files will not change.", ButtonType.YES, ButtonType.NO)).showAndWait().orElse(ButtonType.NO) == ButtonType.YES)
      perform(() -> editor.delete(recipe.id()));
  }

  private void reset(final RecipeDefinition recipe) {
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
        final boolean blocked = readOnlyModRecipe(chosen.get()) || editor.hasConflict(id) || editor.status(id) == RecipeModificationStatus.DELETED;
        duplicate.setDisable(blocked);
        duplicate.setText(blocked ? "Duplicate Recipe (reset deletion/review conflict first)" : "Duplicate Recipe");
        delete.setDisable(blocked);
        delete.setText(blocked ? "Delete Recipe (already deleted or source conflict)" : "Delete Recipe");
        edit.setDisable(readOnlyModRecipe(chosen.get())||editor.status(id) == RecipeModificationStatus.DELETED);
        export.setDisable(readOnlyModRecipe(chosen.get()));
        edit.setText(editor.status(id) == RecipeModificationStatus.DELETED ? "Edit Recipe (Reset Changes restores deleted recipe)" : "Edit Recipe");
        reset.setDisable(!editor.hasConflict(id) && editor.status(id) == RecipeModificationStatus.UNCHANGED);
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
    if (runtimeView != null) runtimeView.close();
    closed = true;
    textures.close();displayCache.clear();
    editor.removeListener(listener);
  }

  private RecipeDisplayNameResolver.Display display(final RecipeDefinition recipe){return displayCache.computeIfAbsent(recipe,r->displayNames.resolve(r,language.getValue()));}

  private boolean matchesSearch(final RecipeDefinition recipe,final String query) {
    final var value=display(recipe);
    return java.util.stream.Stream.concat(java.util.stream.Stream.of(recipe.id().toString(),recipe.id().namespace(),recipe.type().displayId(),value.name(),value.type(),value.machine()),recipe.source().sourceModIds().stream())
        .anyMatch(text->text.toLowerCase(Locale.ROOT).contains(query));
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
        value.previewOutput().flatMap(output->result.registry().find(output.id()).stream().filter(entry->entry.kind()==output.kind()).findFirst()).ifPresent(entry->row.getChildren().addFirst(new ResourcePreview(entry,result,textures,36,()->getItem()==recipe,preview->{})));
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
    source.clear();
    diagnostics.clear();
    if (recipe == null) {
      viewer.getChildren().add(new EmptyStateView("No recipe selected","Select a recipe to inspect its definition, source evidence and diagnostics."));
      return;
    }
    final var presentation=display(recipe);
    viewer.getChildren().addAll(label(presentation.name()),label("Type: "+presentation.type()+" · "+presentation.machine()),label("Recipe ID: "+recipe.id()),label("Recipe status: "+editor.status(recipe.id())+(editor.hasConflict(recipe.id())?" · SOURCE CONFLICT":"")));
    if (recipe.status() == RecipeParseStatus.UNSUPPORTED || recipe.status() == RecipeParseStatus.INVALID)
      viewer.getChildren().add(label("Semantic view unavailable. See Raw JSON and Diagnostics."));
    else {
      final var layout=new RecipeLayoutResolver().resolve(recipe);
      if(layout.isPresent()) {
        final var drawing=new VBox();
        final var scale=new Spinner<Integer>(40,80,64,8);scale.setPrefWidth(100);scale.setAccessibleText("Recipe slot size in pixels");
        final Runnable render=()->{final var view=new RecipeLayoutRenderer(layout.get(),result,textures,scale.getValue(),language.getValue(),RecipeLayoutRenderer.Mode.VIEWER,null);view.resourceNavigation(resourceNavigation);drawing.getChildren().setAll(view);};
        scale.valueProperty().addListener((o,a,b)->render.run());render.run();
        viewer.getChildren().addAll(drawing,new HBox(8,new Label("Slot size"),scale));
      } else {
        viewer.getChildren().add(label("No verified visual layout. Inspect Raw JSON for inputs and outputs."));
        recipe.properties().forEach(property->viewer.getChildren().add(label(property.name()+": "+property.rawValue())));
      }
      final var editButton=new Button("Edit recipe");editButton.setDisable(readOnlyModRecipe(recipe));editButton.setOnAction(event->edit(recipe));
      final var sourceButton=new Button("View Source");sourceButton.setOnAction(event->detailTabs.getSelectionModel().select(2));
      viewer.getChildren().add(new HBox(8,editButton,sourceButton));
    }
    recipe.type().location().flatMap(moddedAdapters::find).ifPresent(adapter->{final var caps=adapter.capabilities(recipe);viewer.getChildren().add(label("View: "+(caps.supports(dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_VIEW)?"SUPPORTED":"UNSUPPORTED")+" · Edit: "+capabilityStatus(caps,dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_EDIT)+" · Create: "+capabilityStatus(caps,dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_CREATE)+" · Export: "+capabilityStatus(caps,dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_EXPORT)));viewer.getChildren().add(label(caps.reason()));});
    viewer.getChildren().add(label("Physical source: "+recipe.source().physicalSource()+" · "+recipe.source().confidence()));
    json.setText(recipe.rawJson());
    json.positionCaret(0);
    final var savedChange = editor.persistentSnapshot().changes().get(recipe.id());
    final var evidence = savedChange == null ? editor.originals().provenance(recipe.id()) : new dev.recipeeditor.exporters.ExportSourceMapping().resolve(savedChange, editor.originals(), projectFile, editor.hasConflict(recipe.id()));
    source.setText(evidence.describe());
    diagnostics.setText(recipe.diagnostics().stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
  }

  private String describe(final IngredientDefinition value) {
    if (value instanceof IngredientDefinition.Item item) return item.id().toString();
    if (value instanceof IngredientDefinition.Tag tag) return "#" + tag.id();
    if (value instanceof IngredientDefinition.Alternatives alternatives)
      return alternatives.choices().stream().map(this::describe).collect(java.util.stream.Collectors.joining(" OR "));
    return "Unsupported ingredient (see JSON)";
  }
}
