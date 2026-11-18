package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.ModpackDefinition;
import dev.recipeeditor.exporters.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Presents saved changes and immutable previews; export services own all I/O. */
public final class ExportWizard {
    private final Stage stage = new Stage();
    private final RecipeExportService service;
    private final ModpackDefinition modpack;
    private final RecipeIndex originals;
    private final RecipeChangeSet saved;
    private final Path project;
    private final Set<ResourceLocation> blocked;
    private final List<RecipeDefinition> copies;
    private final Consumer<Path> openDirectory;
    private ExportWorkspace workspace;
    private final ComboBox<ExportProfile> profiles = new ComboBox<>();
    private final TextField name = new TextField("Development"), target = new TextField();
    private final ComboBox<ExportFormat> format = new ComboBox<>(FXCollections.observableArrayList(ExportFormat.values()));
    private final ComboBox<OverwritePolicy> overwrite = new ComboBox<>(FXCollections.observableArrayList(OverwritePolicy.values()));
    private final CheckBox sourceConsent = new CheckBox("Allow writing into the imported modpack for this export"), reviewed = new CheckBox("I reviewed generated files, replacements and removals");
    private final TableView<RecipeChange> changes = new TableView<>();
    private final TableView<PlannedRecipe> planned = new TableView<>();
    private final ListView<String> files = new ListView<>();
    private final TextArea content = area(), diagnostics = area();
    private final Label counts = new Label(), status = new Label("Select saved changes, then build a preview. Unsaved edits are excluded.");
    private final Button dryRun = new Button("Dry Run"), preview = new Button("Preview"), export = new Button("Export"), cancel = new Button("Cancel"), saveProfile = new Button("Save Profile"), open = new Button("Open result directory");
    private final ProgressBar progress = new ProgressBar(0);
    private final Map<ResourceLocation,ExportFormat> choices = new HashMap<>();
    private String profileMinecraft,profileLoader;
    private final Label version = new Label();
    private String profileId = UUID.randomUUID().toString().replace("-", "");
    private ExportPlan plan;
    private Path resultDirectory;
    private ExportOperation operation;
    private boolean busy, loading;
    private final VBox settings = new VBox(8);
    private final TabPane tabs = new TabPane();

    public ExportWizard(final Window owner, final RecipeExportService service, final ModpackDefinition modpack,
            final RecipeIndex originals, final RecipeChangeSet saved, final Path project, final Set<ResourceLocation> blocked,
            final ExportWorkspace workspace, final Set<ResourceLocation> selection, final ExportFormat initialFormat,
            final List<RecipeDefinition> copies, final Consumer<Path> openDirectory) {
        this.service=service; this.modpack=modpack; this.originals=originals; this.saved=saved; this.project=project;
        profileMinecraft=modpack.minecraftVersion().value();profileLoader=modpack.loader().type().name();
        version.setText("Profile target: "+profileMinecraft+" / "+profileLoader);
        this.blocked=Set.copyOf(blocked); this.workspace=workspace; this.copies=List.copyOf(copies); this.openDirectory=openDirectory;
        stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); stage.setTitle("Recipe Export Wizard — saved project changes");
        format.setValue(initialFormat); overwrite.setValue(OverwritePolicy.REFUSE);
        profiles.setCellFactory(list -> profileCell()); profiles.setButtonCell(profileCell());
        profiles.getItems().setAll(workspace.profiles()); profiles.setPromptText("New profile");
        profiles.valueProperty().addListener((o,a,b)->{if(b!=null) loadProfile(b);});
        final var fresh = new Button("New Profile"); fresh.setOnAction(e->{profiles.getSelectionModel().clearSelection(); profileId=UUID.randomUUID().toString().replace("-", ""); name.setText("Development");profileMinecraft=modpack.minecraftVersion().value();profileLoader=modpack.loader().type().name();version.setText("Profile target: "+profileMinecraft+" / "+profileLoader); invalidate();});
        final var browse = new Button("Browse…"); browse.setOnAction(e->{final var chooser=new DirectoryChooser(); chooser.setTitle("Select an existing export output directory"); final var folder=chooser.showDialog(stage); if(folder!=null) target.setText(folder.toString());});
        HBox.setHgrow(target,Priority.ALWAYS);
        settings.getChildren().addAll(new HBox(10,new Label("Profile"),profiles,fresh,new Label("Name"),name),
                new HBox(10,new Label("Output"),target,browse), new HBox(10,new Label("Format"),format,new Label("Overwrite"),overwrite,new Label("Backups: always enabled")),sourceConsent,version,
                new Label("Target: "+modpack.minecraftVersion().value()+" / "+modpack.loader().type()+". Files generated here are not runtime-validated."));
        name.textProperty().addListener((o,a,b)->invalidate()); target.textProperty().addListener((o,a,b)->invalidate());
        format.valueProperty().addListener((o,a,b)->{choices.clear(); invalidate();}); overwrite.valueProperty().addListener((o,a,b)->invalidate()); sourceConsent.selectedProperty().addListener((o,a,b)->invalidate());
        tabs.getTabs().addAll(tab("Changes",changeSelection(selection)),tab("Plan / format choice",planTable()),tab("Generated files",filePreview()),tab("Diagnostics",diagnostics));
        dryRun.setOnAction(e->buildPreview(false)); preview.setOnAction(e->buildPreview(true)); export.setOnAction(e->apply());
        saveProfile.setOnAction(e->saveProfile()); open.setOnAction(e->{if(resultDirectory!=null) openDirectory.accept(resultDirectory);}); open.setDisable(true);
        cancel.setOnAction(e->{if(operation!=null) {if(operation.cancel()) status.setText("Cancellation requested before file application");} else if(!busy) stage.close();});
        reviewed.selectedProperty().addListener((o,a,b)->updateButtons()); progress.setMaxWidth(Double.MAX_VALUE);
        final var bottom = new VBox(8,reviewed,progress,status,new HBox(10,dryRun,preview,export,saveProfile,open,cancel));
        final var root = new BorderPane(tabs,settings,null,bottom,null); root.setPadding(new Insets(18)); BorderPane.setMargin(tabs,new Insets(12,0,12,0));
        final var scene = new Scene(root,1120,780); scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("theme.css")).toExternalForm()); stage.setScene(scene);
        stage.setOnCloseRequest(e->{if(busy) {e.consume(); if(operation!=null&&!operation.applying()) operation.cancel(); status.setText("Wait for the active operation to finish; cancellation is disabled during file application.");}});
        stage.setMinWidth(900); stage.setMinHeight(600); updateButtons();
    }
    public void show() { stage.show(); }
    private VBox changeSelection(final Set<ResourceLocation> selection) {
        final var rows = FXCollections.observableArrayList(saved.changes().values()); final var visible = new FilteredList<>(rows);
        changes.setItems(visible); changes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        changes.getColumns().add(column("Recipe ID",c->c.id().toString(),330)); changes.getColumns().add(column("Operation",c->c.type().name(),100));
        changes.getColumns().add(column("Source Mod IDs",this::sourceMods,220)); changes.getColumns().add(column("Recipe type",this::recipeTypes,280));
        if(selection.isEmpty()&&copies.isEmpty()) changes.getSelectionModel().selectAll(); else for(final var row:rows) if(selection.contains(row.id())) changes.getSelectionModel().select(row);
        final var mod = new ComboBox<String>(); mod.getItems().add("All source mods"); rows.stream().flatMap(c->mods(c).stream()).distinct().sorted().forEach(mod.getItems()::add); mod.getSelectionModel().selectFirst();
        final var type = new ComboBox<String>(); type.getItems().add("All recipe types"); rows.stream().flatMap(c->definitions(c).stream()).map(r->r.type().displayId()).distinct().sorted().forEach(type.getItems()::add); type.getSelectionModel().selectFirst();
        final Runnable filter=()->{visible.setPredicate(c->(mod.getSelectionModel().getSelectedIndex()==0||mods(c).contains(mod.getValue()))&&(type.getSelectionModel().getSelectedIndex()==0||definitions(c).stream().anyMatch(r->r.type().displayId().equals(type.getValue())))); changes.getSelectionModel().selectAll(); invalidate(); updateCounts();};
        mod.valueProperty().addListener((o,a,b)->filter.run()); type.valueProperty().addListener((o,a,b)->filter.run());
        final var all = new Button("Select all visible"); all.setOnAction(e->changes.getSelectionModel().selectAll()); final var none = new Button("Clear selection"); none.setOnAction(e->changes.getSelectionModel().clearSelection());
        changes.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<RecipeChange>)c->{invalidate();updateCounts();});
        updateCounts(); VBox.setVgrow(changes,Priority.ALWAYS);
        return new VBox(8,new Label("Ctrl/Shift selects saved changes. Mod filters use source ownership; each profile replaces its previous selection."),new HBox(10,mod,type,all,none),counts,changes);
    }
    private List<RecipeDefinition> definitions(final RecipeChange change) {return change.replacement().isEmpty()?originals.findById(change.id()):change.replacement();}
    private Set<String> mods(final RecipeChange change) {final var result=new TreeSet<String>(); change.originals().forEach(r->result.addAll(r.source().modIds())); definitions(change).forEach(r->result.addAll(r.source().modIds())); return result;}
    private String sourceMods(final RecipeChange c) {return String.join(", ",mods(c));}
    private String recipeTypes(final RecipeChange c) {return definitions(c).stream().map(r->r.type().displayId()).distinct().collect(Collectors.joining(", "));}
    private void updateCounts() {final var selected=changes.getSelectionModel().getSelectedItems(); counts.setText("Created: "+selected.stream().filter(c->c.type()==RecipeChangeType.CREATE).count()+"   Modified: "+selected.stream().filter(c->c.type()==RecipeChangeType.UPDATE).count()+"   Deleted: "+selected.stream().filter(c->c.type()==RecipeChangeType.DELETE).count()+"   Explicit unchanged copies: "+copies.size());}
    private TableView<PlannedRecipe> planTable() {
        planned.getColumns().add(column("Recipe / operation",p->p.change().id()+" / "+p.change().type(),330)); planned.getColumns().add(column("Format",p->p.format().name(),110));
        planned.getColumns().add(column("Capability",p->p.capability().status()+": "+p.capability().reason(),430)); planned.getColumns().add(column("Alternatives",p->p.alternatives().toString(),220));
        final var menu=new ContextMenu(); for(final var choice:ExportFormat.values()) {final var item=new MenuItem("Use "+choice); item.setOnAction(e->{final var selected=planned.getSelectionModel().getSelectedItem();if(selected!=null) {choices.put(selected.change().id(),choice);invalidate();status.setText("Format changed for "+selected.change().id()+". Rebuild Preview.");}});menu.getItems().add(item);} planned.setContextMenu(menu);return planned;
    }
    private SplitPane filePreview() {files.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{content.setText(plan==null||b==null?"":plan.files().getOrDefault(b,"This previously generated file will be removed after its ownership hash is rechecked."));content.positionCaret(0);}); return new SplitPane(files,content);}
    private ExportProfile profile() {return new ExportProfile(profileId,name.getText(),Path.of(target.getText()),format.getValue(),overwrite.getValue(),true,profileMinecraft,profileLoader);}
    private void loadProfile(final ExportProfile p) {loading=true;profileMinecraft=p.minecraftVersion();profileLoader=p.loader();version.setText("Profile target: "+profileMinecraft+" / "+profileLoader);profileId=p.id();name.setText(p.name());target.setText(p.targetDirectory().toString());format.setValue(p.preferredFormat());overwrite.setValue(p.overwritePolicy());loading=false;invalidate();}
    private ExportRequest request() {final var context=new ExportContext(modpack,originals,saved,project,profile(),workspace.manifests(),blocked,sourceConsent.isSelected());return new ExportRequest(context,changes.getSelectionModel().getSelectedItems().stream().map(RecipeChange::id).collect(Collectors.toSet()),choices,copies);}
    private void buildPreview(final boolean showFiles) {
        try {final var request=request();setBusy(true);status.setText("Validating saved changes and generating preview; no files are written…");progress.setProgress(-1);
            service.dryRun(request).whenComplete((result,error)->Platform.runLater(()->{setBusy(false);progress.setProgress(0);if(error!=null){failed(error);return;}plan=result;reviewed.setSelected(false);planned.getItems().setAll(result.recipes());files.getItems().setAll(result.files().keySet());result.obsoleteFiles().forEach(path->files.getItems().add(path));files.getSelectionModel().selectFirst();content.setText(files.getSelectionModel().getSelectedItem()==null?"No generated files. See Diagnostics.":result.files().getOrDefault(files.getSelectionModel().getSelectedItem(),"This obsolete file will be removed after its ownership hash is rechecked."));content.positionCaret(0);diagnostics.setText(describe(result.diagnostics())+"\n\nOverwrites (existing hashes):\n"+result.expectedHashes()+"\n\nRemovals:\n"+result.obsoleteFiles());status.setText("Dry Run: "+result.recipes().stream().filter(r->r.capability().supported()).count()+" supported / "+result.recipes().size()+" selected; "+result.files().size()+" generated files. "+(result.valid()?"Review files before Export.":"Errors block Export; see Diagnostics."));tabs.getSelectionModel().select(showFiles?2:1);updateButtons();}));
        }catch(RuntimeException invalid){failed(invalid);}
    }
    private void saveProfile() {try{final var current=profile();setBusy(true);service.saveProfile(project,current).thenCompose(ignored->service.workspace(project)).whenComplete((result,error)->Platform.runLater(()->{setBusy(false);if(error!=null){failed(error);return;}workspace=result;profiles.getItems().setAll(result.profiles());status.setText("Profile saved beside the project. Dry Run never saves automatically.");}));}catch(RuntimeException error){failed(error);}}
    private void apply() {
        if(plan==null||!plan.valid()||!reviewed.isSelected())return;
        resultDirectory=plan.request().context().profile().targetDirectory();setBusy(true);operation=service.export(plan,value->Platform.runLater(()->{progress.setProgress(value.fraction());status.setText(value.stage());cancel.setDisable(value.applying());}));
        operation.result().whenComplete((result,error)->Platform.runLater(()->{operation=null;if(error!=null){setBusy(false);failed(error);return;}diagnostics.appendText("\n\nRESULT: "+result.status()+"\n"+describe(result.diagnostics()));status.setText(result.status()+" — see Diagnostics. Minecraft runtime validation has not been performed.");progress.setProgress(result.status()==ExportResult.Status.SUCCESS?1:0);open.setDisable(result.status()!=ExportResult.Status.SUCCESS);reviewed.setSelected(false);export.setDisable(true);tabs.getSelectionModel().select(3);
            // Ownership information must be refreshed before the next export in this window.
            service.workspace(project).whenComplete((fresh,failure)->Platform.runLater(()->{if(failure==null)workspace=fresh;else failed(failure);plan=null;setBusy(false);updateButtons();}));}));
    }
    private String describe(final List<ExportDiagnostic> entries) {return entries.stream().map(d->d.level()+" "+d.code()+" "+d.recipeIds()+" ["+d.format()+"]\n"+d.message()+"\nAction: "+d.suggestedAction()).collect(Collectors.joining("\n\n"));}
    private void invalidate() {if(loading)return;plan=null;reviewed.setSelected(false);open.setDisable(true);status.setText("Settings or selection changed. Rebuild Preview before Export.");updateButtons();}
    private void setBusy(final boolean value) {busy=value;settings.setDisable(value);tabs.getTabs().getFirst().getContent().setDisable(value);planned.setDisable(value);updateButtons();}
    private void updateButtons() {dryRun.setDisable(busy);preview.setDisable(busy);saveProfile.setDisable(busy);export.setDisable(busy||plan==null||!plan.valid()||!reviewed.isSelected());reviewed.setDisable(busy||plan==null);cancel.setDisable(busy&&operation==null);}
    private void failed(final Throwable error) {Throwable root=error;while(root.getCause()!=null)root=root.getCause();status.setText("Export operation failed: "+root.getMessage());diagnostics.setText(root.toString());tabs.getSelectionModel().select(3);}
    private <T> TableColumn<T,String> column(final String title,final java.util.function.Function<T,String> text,final int width) {final var c=new TableColumn<T,String>(title);c.setCellValueFactory(value->new ReadOnlyStringWrapper(text.apply(value.getValue())));c.setPrefWidth(width);return c;}
    private ListCell<ExportProfile> profileCell() {return new ListCell<>() {@Override protected void updateItem(final ExportProfile p,final boolean empty){super.updateItem(p,empty);setText(empty||p==null?null:p.name());}};}
    private TextArea area() {final var result=new TextArea();result.setEditable(false);return result;}
    private Tab tab(final String title,final javafx.scene.Node node) {final var tab=new Tab(title,node);tab.setClosable(false);return tab;}
}
