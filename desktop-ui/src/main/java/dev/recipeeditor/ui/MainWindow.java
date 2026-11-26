package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.CompanionConnectionService;
import dev.recipeeditor.companionclient.RecipeMergeService;
import dev.recipeeditor.companionclient.RegistryMergeService;
import dev.recipeeditor.companionclient.RuntimeSnapshotService;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ApplicationInfo;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.exporters.ExportFormat;
import dev.recipeeditor.exporters.ExportWorkspace;
import dev.recipeeditor.exporters.RecipeExportService;
import dev.recipeeditor.integrations.MinecraftCompatibilityRegistry;
import dev.recipeeditor.integrations.RecipeFormatAdapter;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.MaterialDefinition;
import dev.recipeeditor.registry.ResourceKind;
import dev.recipeeditor.registry.TextureResourceService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.*;

/**
 * Desktop shell coordinates presentation; services own all file access and analysis.
 */
public final class MainWindow {
  private final RuntimeIntegrationView runtime;
  private final ApplicationInfo info;
  private final ModpackImportService importer;
  private final StaticAnalysisService analyzer;
  private final TextureResourceService textures;
  private final BorderPane workspace = new BorderPane();
  private final NotificationBanner notifications = new NotificationBanner();
  private final Label breadcrumb = new Label(), status = new Label("Ready"), project = new Label("No project");
  private final Button importButton = new Button("Import Modpack"), indexButton = new Button("Index resources"), diagnosticsButton = new Button("Diagnostics");
  private final Map<NavigationSection, ToggleButton> navigation = new EnumMap<>(NavigationSection.class);
  private final ProjectPersistence persistence;
  private final java.util.function.BiFunction<DiscoveryResult, RecipeFormatAdapter, RecipeEditorService> editorFactory;
  private final EditorTasks editorTasks = new EditorTasks();
  private final Button openProjectButton = new Button("Open Project"), saveProjectButton = new Button("Save Project");
  private final RecipeExportService exports;
  private final java.util.function.Consumer<Path> openDirectory;
  private final Button exportButton = new Button("Export All Changes");
  private RecipeEditorService editor;
  private Path projectFile, pendingProjectFile;
  private ProjectDocument pendingProject;
  private boolean projectSaving;
  private final ModCatalogView catalog = new ModCatalogView();
  private ImportOperation importing;
  private DiscoveryOperation indexing;
  private ModpackImportResult imported;
  private DiscoveryResult discovered;
  private ResourceBrowserView resources;
  private MaterialsBrowserView materials;
  private RecipeBrowserView recipes;
  private StaticRecipeCatalogView staticRecipes;
  private final MinecraftCompatibilityRegistry compatibility = new MinecraftCompatibilityRegistry();
  private Stage stage;
  private boolean closed, busy;
  private long generation;
  private final java.util.prefs.Preferences directoryPreferences=java.util.prefs.Preferences.userNodeForPackage(MainWindow.class);

  public MainWindow(final ApplicationInfo info, final ModpackImportService importer, final StaticAnalysisService analyzer, final TextureResourceService textures, final ProjectPersistence persistence, final java.util.function.BiFunction<DiscoveryResult, RecipeFormatAdapter, RecipeEditorService> editorFactory, final RecipeExportService exports, final java.util.function.Consumer<Path> openDirectory, final CompanionConnectionService connection, final RuntimeSnapshotService snapshots) {
    this.runtime = new RuntimeIntegrationView(connection, snapshots, this::applyRuntime);
    this.exports = Objects.requireNonNull(exports);
    this.openDirectory = Objects.requireNonNull(openDirectory);
    this.info = Objects.requireNonNull(info);
    this.importer = Objects.requireNonNull(importer);
    this.analyzer = Objects.requireNonNull(analyzer);
    this.textures = Objects.requireNonNull(textures);
    this.persistence = Objects.requireNonNull(persistence);
    this.editorFactory = Objects.requireNonNull(editorFactory);
  }

  public void show(final Stage stage) {
    this.stage = stage;
    stage.setOnHidden(e -> {
      closed = true;
      runtime.close();
      cancel();
      if (resources != null) resources.deactivate();
      if (materials != null) materials.deactivate();
      if (recipes != null) recipes.close();
      editorTasks.close();
    });
    stage.setOnCloseRequest(e -> {
      if (projectSaving) {
        e.consume();
        status.setText("Wait for project save to finish");
      } else if (editor != null && editor.dirty()) {
        e.consume();
        confirmLeave(stage::hide);
      }
    });
    final var root = new BorderPane();
    root.setTop(toolbar());
    root.setLeft(navigation());
    root.setCenter(workspace);
    root.setBottom(statusbar());
    workspace.setTop(notifications);
    final var scene = new Scene(root, 1320, 820);
    new UiTheme().apply(scene);
    scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
      if (busy || editor == null || event.getTarget() instanceof TextInputControl || !event.isControlDown()) return;
      if (event.getCode() == javafx.scene.input.KeyCode.Z || event.getCode() == javafx.scene.input.KeyCode.Y) {
        final boolean redo = event.getCode() == javafx.scene.input.KeyCode.Y || event.isShiftDown();
        event.consume();
        editorTasks.submit(() -> {
          if (redo) editor.redo();
          else editor.undo();
          return true;
        }, done -> updateProjectState(), error -> new UiTheme().apply(new Alert(Alert.AlertType.ERROR, rootMessage(error), ButtonType.OK)).showAndWait());
      }
    });
    stage.setTitle(info.name());
    stage.setMinWidth(1000);
    stage.setMinHeight(620);
    stage.setScene(scene);
    select(NavigationSection.PROJECTS);
    stage.show();
  }

  private VBox toolbar() {
    final var title = new Label(info.name());
    title.getStyleClass().add("brand");
    final var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    importButton.setOnAction(e -> choose());
    indexButton.setOnAction(e -> analyze());
    indexButton.setDisable(true);
    diagnosticsButton.setOnAction(e -> showDiagnostics());
    diagnosticsButton.setDisable(true);
    openProjectButton.setOnAction(e -> confirmLeave(this::openProject));
    saveProjectButton.setOnAction(e -> saveProject(() -> {
    }));
    saveProjectButton.setDisable(true);
    final var primary = new ToolBar(title, new Separator(), breadcrumb, spacer, openProjectButton, saveProjectButton, new Label(info.version()));
    exportButton.setOnAction(e -> openExport(null, ExportFormat.AUTO));
    exportButton.setDisable(true);
    final var integrationInfo = new Button("GregTech capabilities");
    integrationInfo.setOnAction(event -> {
      final var registry = new dev.recipeeditor.integrations.api.IntegrationRegistry();
      registry.register(new dev.recipeeditor.integrations.gregtech.GregTechIntegration());
      final var context = imported == null ? new dev.recipeeditor.integrations.api.IntegrationContext("UNKNOWN", ModLoaderType.UNKNOWN, Map.of(), Map.of()) : dev.recipeeditor.integrations.api.IntegrationContext.from(imported.modpack());
      final var value = registry.capabilities("gtceu", context);
      final var alert = new UiTheme().apply(new Alert(Alert.AlertType.INFORMATION, "Detected GTCEu: " + context.mods().getOrDefault("gtceu", "NOT INSTALLED") + "\nCompatibility: " + value.compatibility() + "\nAdapter: " + value.adapterVersion() + "\nFeatures: " + value.features() + "\n" + String.join("\n", value.diagnostics()), ButtonType.OK));
      alert.initOwner(stage);
      alert.setHeaderText("GregTech capabilities for imported environment");
      alert.showAndWait();
    });
    final var secondary = new ToolBar(importButton, indexButton, diagnosticsButton, exportButton, integrationInfo);
    final var toolbar = new VBox(primary, secondary);
    toolbar.getStyleClass().add("shell-toolbar");
    return toolbar;
  }

  private VBox navigation() {
    final var box = new VBox(6);
    box.getStyleClass().add("navigation");
    box.setPrefWidth(185);
    final var heading = new Label("WORKSPACE");
    heading.getStyleClass().add("muted");
    VBox.setMargin(heading, new Insets(8, 12, 16, 12));
    box.getChildren().add(heading);
    final var group = new ToggleGroup();
    for (final var section : NavigationSection.values()) {
      final var button = new ToggleButton(section.title());
      button.setToggleGroup(group);
      button.setMaxWidth(Double.MAX_VALUE);
      button.setAlignment(Pos.CENTER_LEFT);
      button.setOnAction(e -> select(section));
      navigation.put(section, button);
      box.getChildren().add(button);
    }
    return box;
  }

  private HBox statusbar() {
    final var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    final var box = new HBox(16, status, spacer, project, new Label("v" + info.version()));
    box.getStyleClass().add("status-bar");
    return box;
  }

  private void select(final NavigationSection section) {
    if (busy) return;
    if (resources != null) resources.deactivate();
    if (materials != null) materials.deactivate();
    navigation.get(section).setSelected(true);
    breadcrumb.setText(section.title());
    if (section == NavigationSection.RUNTIME) {
      workspace.setCenter(runtime);
      return;
    }
    if (imported != null && (section == NavigationSection.PROJECTS || section == NavigationSection.MODS)) {
      workspace.setCenter(catalog);
      return;
    }
    if (discovered != null) {
      if (section == NavigationSection.MATERIALS && materials != null) {
        materials.activate();
        workspace.setCenter(materials);
        return;
      }
      if (section == NavigationSection.RECIPES) {
        if (recipes != null) recipes.all();
        workspace.setCenter(recipes == null ? staticRecipes : recipes);
        return;
      }
      final var kind = switch (section) {
        case ITEMS -> ResourceKind.ITEM;
        case FLUIDS -> ResourceKind.FLUID;
        default -> null;
      };
      if (kind != null) {
        workspace.setCenter(resources);
        resources.show(kind);
        return;
      }
    }
    if(section==NavigationSection.SETTINGS || section==NavigationSection.ANALYZER) {
      workspace.setCenter(new EmptyStateView(section.emptyTitle(),section.description()));
    } else if(section==NavigationSection.MATERIALS) {
      workspace.setCenter(new EmptyStateView(section.emptyTitle(),section.description(),"Open Runtime",()->select(NavigationSection.RUNTIME)));
    } else {
      workspace.setCenter(new EmptyStateView(section.emptyTitle(),section.description(),"Import Modpack",this::choose));
    }
  }

  private void choose() {
    confirmLeave(this::chooseDirectory);
  }

  private void chooseDirectory() {
    final var chooser = new DirectoryChooser();
    chooser.setTitle("Select one Minecraft instance");
    final String previous=directoryPreferences.get("lastModpackDirectory","");
    if(!previous.isBlank())try {
      Path candidate=Path.of(previous);
      while(candidate!=null&&!java.nio.file.Files.isDirectory(candidate))candidate=candidate.getParent();
      if(candidate!=null)chooser.setInitialDirectory(candidate.toFile());
    } catch(IllegalArgumentException ignored) { /* A removed or invalid preference must not prevent import. */ }
    final var directory = chooser.showDialog(stage);
    if (directory == null) return;
    directoryPreferences.put("lastModpackDirectory",directory.toPath().toAbsolutePath().normalize().toString());
    pendingProject = null;
    pendingProjectFile = null;
    importDirectory(directory.toPath());
  }

  private void importDirectory(final Path directory) {
    final long request = ++generation;
    setBusy(true);
    final var view = progressView("IMPORT MINECRAFT MODPACK", directory.toString());
    importing = importer.start(directory, value -> Platform.runLater(() -> update(view, value, request)));
    final var operation = importing;
    operation.result().whenComplete((result, error) -> Platform.runLater(() -> {
      if (closed || request != generation) return;
      importing = null;
      if (error != null) {
        finishError(operation.result().isCancelled(), "Import", error);
        return;
      }
      imported = withManualMinecraftVersion(result);
      runtime.selectModpack(null);
      catalog.show(imported);
      project.setText(result.modpack().selectedDirectory().getFileName().toString());
      discovered = null;
      if (resources != null) resources.deactivate();
      if (materials != null) materials.deactivate();
      textures.clear();
      if (recipes != null) recipes.close();
      resources = null;
      materials = null;
      recipes = null;
      staticRecipes = null;
      editor = null;
      projectFile = null;
      analyze();
    }));
  }

  private void analyze() {
    if (imported == null) return;
    textures.clear();
    final RecipeChangeSet restoreChanges = editor == null ? null : editor.persistentSnapshot();
    final RecipeChangeSet persistedChanges = editor == null ? null : editor.persistedSnapshot();
    final long request = ++generation;
    setBusy(true);
    if (resources != null) resources.deactivate();
    final var view = progressView("INDEXING MODPACK", imported.modpack().selectedDirectory().toString());
    indexing = analyzer.start(imported.modpack(), value -> Platform.runLater(() -> update(view, value, request)));
    final var operation = indexing;
    operation.result().whenComplete((result, error) -> Platform.runLater(() -> {
      if (closed || request != generation) return;
      indexing = null;
      if (error != null) {
        finishError(operation.result().isCancelled(), "Indexing", error);
        return;
      }
      final var version = imported.modpack().minecraftVersion().value();
      final var editableFormat = compatibility.find(version).filter(RecipeFormatAdapter::editingSupported);
      if (editableFormat.isEmpty()) {
        discovered = result;
        resources = createResources(result);
        materials = new MaterialsBrowserView(result, textures, this::forForm);
        staticRecipes = new StaticRecipeCatalogView(result.recipes());
        runtime.selectModpack(imported.modpack());
        setBusy(false);
        select(NavigationSection.RECIPES);
        status.setText("Minecraft " + version + " · " + result.recipes().recipes().size()
                + " static recipes · no verified editor for this format"
                + (pendingProject == null ? "" : " · saved project changes were not applied"));
        return;
      }
      editorTasks.submit(() -> {
        final var prepared = editorFactory.apply(result, editableFormat.orElseThrow());
        if (pendingProject != null)
          prepared.restore(pendingProject.materialize(prepared.validator(), pendingProjectFile));
        else if (restoreChanges != null) {
          prepared.restore(restoreChanges);
          prepared.markPersisted(persistedChanges);
        }
        return prepared;
      }, prepared -> {
        if (closed || request != generation) return;
        if (recipes != null) recipes.close();
        discovered = result;
        editor = prepared;
        if (pendingProject != null) {
          projectFile = pendingProjectFile;
          pendingProject = null;
          pendingProjectFile = null;
        }
        resources = createResources(result);
        materials = new MaterialsBrowserView(result, textures, this::forForm);
        recipes = new RecipeBrowserView(result, editor, editorTasks, this::openExport, dev.recipeeditor.integrations.api.IntegrationContext.from(imported.modpack()));
        recipes.resourceNavigation((kind,id)->{select(kind==ResourceKind.FLUID?NavigationSection.FLUIDS:NavigationSection.ITEMS);resources.showResource(kind,id);});
        editor.addListener(id -> Platform.runLater(() -> {
          if (!closed) updateProjectState();
        }));
        setBusy(false);
        select(NavigationSection.ITEMS);
        updateProjectState();
        runtime.selectModpack(imported.modpack());
        status.setText("Completed: " + result.registry().entries().size() + " resources, " + result.recipes().recipes().size() + " recipes · " + editor.conflicts().size() + " blocked changes");
        notifications.show(editor.conflicts().isEmpty()?NotificationBanner.Kind.SUCCESS:NotificationBanner.Kind.WARNING,status.getText());
      }, failure -> finishError(false, "Restore project", failure));
    }));
  }

  private ModpackImportResult withManualMinecraftVersion(final ModpackImportResult result) {
    final var pack = result.modpack();
    if (pack.minecraftVersion().known()) return result;
    final var dialog = new TextInputDialog();
    dialog.initOwner(workspace.getScene().getWindow());
    dialog.setTitle("Minecraft version");
    dialog.setHeaderText("Minecraft version could not be determined");
    dialog.setContentText("Enter the release number, or cancel to index resources only:");
    new UiTheme().apply(dialog.getDialogPane());
    final var input = dialog.getEditor().textProperty();
    dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty()
            .bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> !input.get().strip().matches("[0-9]{1,4}\\.[0-9]{1,4}(?:\\.[0-9]{1,4})?"), input));
    return dialog.showAndWait().map(String::strip).map(version -> new ModpackImportResult(
            new ModpackDefinition(pack.selectedDirectory(), pack.gameDirectory(), pack.structure(),
                    new MinecraftVersion(version, List.of("Manual selection")), pack.loader(), pack.jars()),
            result.diagnostics())).orElse(result);
  }

  private OperationProgressView progressView(final String title, final String path) {
    notifications.dismiss();
    final var view = new OperationProgressView(title, path, this::cancel);
    workspace.setCenter(view);
    return view;
  }

  private void update(final OperationProgressView view, final ImportProgress progress, final long request) {
    if (closed || request != generation || !busy) return;
    view.update(progress);
    status.setText(progress.stage());
  }

  private void cancel() {
    if (importing != null) importing.cancel();
    if (indexing != null) indexing.cancel();
  }

  private void setBusy(final boolean value) {
    busy = value;
    exportButton.setDisable(value || editor == null);
    openProjectButton.setDisable(value);
    saveProjectButton.setDisable(value || editor == null);
    importButton.setDisable(value);
    indexButton.setDisable(value || imported == null);
    diagnosticsButton.setDisable(value || imported == null);
    navigation.values().forEach(button -> button.setDisable(value));
  }

  private void finishError(final boolean cancelled, final String action, final Throwable error) {
    setBusy(false);
    select(NavigationSection.PROJECTS);
    status.setText(action + (cancelled ? " cancelled" : " failed: " + error.getMessage()));
    if (!cancelled) {
      notifications.show(NotificationBanner.Kind.ERROR,action+" failed. Use Diagnostics or the logs for details.");
      workspace.setCenter(new EmptyStateView(action+" failed",rootMessage(error)+". Use Import Modpack or Index resources to retry.","Import Modpack",this::choose));
    } else {
      notifications.show(NotificationBanner.Kind.INFO,action+" cancelled");
    }
  }

  private void forItem(final ResourceLocation id, final boolean uses) {
    if (recipes == null) return;
    select(NavigationSection.RECIPES);
    recipes.forItem(id, uses);
  }

  private ResourceBrowserView createResources(final DiscoveryResult result) {
    return new ResourceBrowserView(result, textures, query -> {
      if (recipes == null) return;
      select(NavigationSection.RECIPES);
      recipes.forResource(query.kind(), query.id(), query.uses());
    },
        (entry, uses) -> editor != null && !(uses ? editor.effective().findRecipesUsing(entry.kind(), entry.id()) : editor.effective().findRecipesProducing(entry.kind(), entry.id())).isEmpty());
  }

  private void forForm(final MaterialDefinition.Form form) {
    select(switch (form.kind()) {
      case ITEM -> NavigationSection.ITEMS;
      case BLOCK -> NavigationSection.ITEMS;
      case FLUID -> NavigationSection.FLUIDS;
    });
    resources.showResource(form.kind(), form.resourceId());
  }

  private void showDiagnostics() {
    final var area = new TextArea();
    area.setEditable(false);
    area.setWrapText(true);
    final var text = new StringBuilder("IMPORT\n");
    imported.diagnostics().forEach(d -> text.append(d).append("\n"));
    if (discovered != null) {
      text.append("\nSTATIC INDEX\n");
      discovered.diagnostics().forEach(d -> text.append(d).append("\n"));
    }
    area.setText(text.toString());
    area.positionCaret(0);
    workspace.setCenter(area);
    breadcrumb.setText("Diagnostics");
  }

  private void updateProjectState() {
    if (recipes != null) recipes.projectFile(projectFile);
    saveProjectButton.setDisable(busy || editor == null);
    exportButton.setDisable(busy || editor == null);
    if (editor != null)
      project.setText((projectFile == null ? "Unsaved project" : projectFile.getFileName().toString()) + (editor.dirty() ? " *" : "") + " · " + editor.conflicts().size() + " conflicts");
  }

  private void confirmLeave(final Runnable continuation) {
    if (editor == null || !editor.dirty()) {
      continuation.run();
      return;
    }
    final var save = new ButtonType("Save Project");
    final var discard = new ButtonType("Discard project changes");
    final var answer = new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION, "Project changes have not been saved to disk. Save before leaving to keep them.", save, discard, ButtonType.CANCEL)).showAndWait().orElse(ButtonType.CANCEL);
    if (answer == save) saveProject(continuation);
    else if (answer == discard) continuation.run();
  }

  private void saveProject(final Runnable after) {
    if (editor == null || projectSaving) return;
    Path target = projectFile;
    if (target == null) {
      final var chooser = new FileChooser();
      chooser.setTitle("Save project outside the source modpack");
      chooser.setInitialFileName("project.json");
      chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Recipe Editor project", "*.json"));
      final var selected = chooser.showSaveDialog(stage);
      if (selected == null) return;
      target = selected.toPath();
    }
    final var destination = target;
    final var current = editor;
    final var snapshot = current.persistentSnapshot();
    final var document = ProjectDocument.capture(imported.modpack().selectedDirectory(), snapshot);
    projectSaving = true;
    setBusy(true);
    status.setText("Saving project...");
    persistence.save(destination, document).whenComplete((ignored, error) -> Platform.runLater(() -> {
      projectSaving = false;
      if (closed) return;
      setBusy(false);
      if (error != null) {
        status.setText("Project save failed");
        new UiTheme().apply(new Alert(Alert.AlertType.ERROR, rootMessage(error), ButtonType.OK)).showAndWait();
        return;
      }
      if (editor == current) {
        current.markPersisted(snapshot);
        projectFile = destination;
        recipes.updateTracking();
        updateProjectState();
      }
      status.setText("Project saved. Source JAR files unchanged.");
      notifications.show(NotificationBanner.Kind.SUCCESS,"Project saved to "+destination.getFileName());
      after.run();
    }));
  }

  private void openProject() {
    final var chooser = new FileChooser();
    chooser.setTitle("Open Recipe Editor project (or backup)");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Project / backup", "*.json", "*.bak"));
    final var chosen = chooser.showOpenDialog(stage);
    if (chosen == null) return;
    setBusy(true);
    status.setText("Loading project...");
    persistence.load(chosen.toPath()).whenComplete((document, error) -> Platform.runLater(() -> {
      if (closed) return;
      if (error != null) {
        setBusy(false);
        new UiTheme().apply(new Alert(Alert.AlertType.ERROR, rootMessage(error), ButtonType.OK)).showAndWait();
        return;
      }
      pendingProject = document;
      pendingProjectFile = chosen.toPath();
      importDirectory(document.modpackDirectory());
    }));
  }

  private Path canonical(final Path path) {
    try {
      return path.toRealPath();
    } catch (java.io.IOException error) {
      throw new java.io.UncheckedIOException(error);
    }
  }

  private record RuntimeMerge(RegistryMergeService.Result registry, RecipeMergeService.Result recipes) {
  }

  private void applyRuntime(final RuntimeData.Snapshot snapshot, final Path file) {
    if (busy || projectSaving) {
      runtime.addDiagnostic("Snapshot saved. A project operation is active; open this snapshot again after it finishes to merge.");
      return;
    }
    if (imported == null || editor == null || discovered == null) {
      runtime.addDiagnostic("Snapshot retained. Import/open its matching Minecraft instance before merging with a project.");
      return;
    }
    final var pack = imported.modpack();
    final var source = discovered;
    final var current = editor;
    final long request = generation;
    setBusy(true);
    editorTasks.submit(() -> {
      if (!Path.of(snapshot.environment().sourceInstance()).toAbsolutePath().normalize().equals(pack.gameDirectory().toAbsolutePath().normalize()) || !canonical(Path.of(snapshot.environment().sourceInstance())).equals(canonical(pack.gameDirectory())))
        throw new IllegalArgumentException("Runtime source instance differs from imported game directory; merge refused");
      if (!snapshot.environment().minecraftVersion().equals(pack.minecraftVersion().value()) || !snapshot.environment().loader().equals(pack.loader().type().name()) || !snapshot.environment().loaderVersion().equals(pack.loader().version()))
        throw new IllegalArgumentException("Runtime Minecraft/loader version differs from imported metadata; re-import the matching instance");
      final var actualVersions = snapshot.mods().stream().collect(java.util.stream.Collectors.toMap(dev.recipeeditor.companionprotocol.RuntimeData.Mod::id, dev.recipeeditor.companionprotocol.RuntimeData.Mod::version));
      for (final var mod : pack.mods()) {
        final var metadata = mod.metadata();
        final String version = metadata.version();
        if (!version.equals("UNKNOWN") && !version.contains("${") && !version.equals(actualVersions.get(metadata.id())))
          throw new IllegalArgumentException("Runtime mod list differs from imported metadata: " + metadata.id() + ". Re-import the matching running instance.");
      }
      final var registry = new RegistryMergeService().merge(source.registry(), snapshot, file);
      final var recipes = new RecipeMergeService().merge(current.originals(), snapshot);
      current.applyRuntimeEvidence(registry.preferred(), registry.runtimeTags(), recipes.changedOrMissing(), snapshot.recipes().stream().map(r -> ResourceLocation.parse(r.id())).collect(java.util.stream.Collectors.toSet()), snapshot.environment().sourceInstance() + " @ " + snapshot.environment().collectedAt());
      return new RuntimeMerge(registry, recipes);
    }, merged -> {
      setBusy(false);
      if (closed || request != generation || editor != current) return;
      final var unified = new DiscoveryResult(source.selectedRoot(), merged.registry().preferred(), new RecipeIndex(source.recipes().recipes(), merged.registry().runtimeTags()), source.diagnostics(), source.elapsedMillis());
      if (resources != null) resources.deactivate();
      if (materials != null) materials.deactivate();
      resources = createResources(unified);
      materials = new MaterialsBrowserView(unified, textures, this::forForm);
      recipes.runtime(merged.recipes());
      runtime.enableRecipeCreation(snapshot, serializer -> {
        if (!busy && editor == current && recipes != null) {
          select(NavigationSection.RECIPES);
          recipes.createFromRuntime(serializer);
        }
      });
      runtime.addDiagnostic("Runtime merged for matching instance. Static definitions and all persistent/unsaved project changes retained; " + current.conflicts().size() + " changes require conflict review. Undo history is reset only if new runtime conflicts were quarantined.");
      runtime.addDiagnostics(merged.registry().diagnostics());
      runtime.addDiagnostics(merged.recipes().diagnostics().stream().filter(message -> message.contains("differ") || message.contains("absent")).toList());
      updateProjectState();
      status.setText("Runtime snapshot merged: " + snapshot.recipes().size() + " recipes; normalized runtime recipes are read only.");
    }, failure -> {
      setBusy(false);
      runtime.addDiagnostic("Merge blocked: " + rootMessage(failure) + ". Snapshot remains available in Runtime.");
    });
  }

  private void openExport(final RecipeDefinition selected, final ExportFormat format) {
    if (editor == null || busy) return;
    if (projectFile == null) {
      saveProject(() -> openExport(selected, format));
      return;
    }
    final var current = editor;
    final var file = projectFile;
    final var pack = imported.modpack();
    final long request = generation;
    setBusy(true);
    status.setText("Loading saved project for export...");
    persistence.load(file).thenCombine(exports.workspace(file), (document, workspace) -> {
      if (!document.modpackDirectory().toAbsolutePath().normalize().equals(pack.selectedDirectory().toAbsolutePath().normalize()))
        throw new IllegalStateException("Saved project references a different modpack; re-open it before export");
      return new Object[]{document, workspace};
    }).whenComplete((loaded, error) -> Platform.runLater(() -> {
      if (closed || request != generation) return;
      if (error != null) {
        setBusy(false);
        new UiTheme().apply(new Alert(Alert.AlertType.ERROR, rootMessage(error), ButtonType.OK)).showAndWait();
        return;
      }
      editorTasks.submit(() -> ((ProjectDocument) loaded[0]).materialize(current.validator(), file), saved -> {
        setBusy(false);
        if (closed || request != generation) return;
        final var copies = new ArrayList<RecipeDefinition>();
        if (selected != null && !saved.changes().containsKey(selected.id())) {
          final var originals = current.originals().findById(selected.id());
          if (current.snapshot().changes().containsKey(selected.id()) || originals.size() != 1) {
            new UiTheme().apply(new Alert(Alert.AlertType.WARNING, "This recipe has unsaved changes, no original, or ambiguous sources. Save the project or select one unambiguous unchanged recipe before export.", ButtonType.OK)).showAndWait();
            return;
          }
          if (new UiTheme().apply(new Alert(Alert.AlertType.CONFIRMATION, "Export an explicit unchanged copy of " + selected.id() + "? This does not add a project change. The original ID is preserved.", ButtonType.YES, ButtonType.NO)).showAndWait().orElse(ButtonType.NO) != ButtonType.YES)
            return;
          copies.add(originals.getFirst());
        }
        final var blocked = current.conflicts().stream().map(SourceConflict::recipeId).collect(java.util.stream.Collectors.toSet());
        new ExportWizard(stage, exports, pack, current.originals(), saved, file, blocked, (ExportWorkspace) loaded[1], selected == null ? Set.of() : Set.of(selected.id()), format, copies, openDirectory).show();
        status.setText("Export uses the project snapshot saved on disk. Unsaved edits are excluded.");
      }, failure -> {
        setBusy(false);
        new UiTheme().apply(new Alert(Alert.AlertType.ERROR, rootMessage(failure), ButtonType.OK)).showAndWait();
      });
    }));
  }

  private String rootMessage(final Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) current = current.getCause();
    return current.getMessage();
  }
}
