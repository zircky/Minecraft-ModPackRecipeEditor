package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.RecipeMergeService.Entry;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.recipeengine.RecipeFilterMode;
import java.util.*;
import java.util.function.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class RuntimeRecipeBrowserView extends BorderPane implements AutoCloseable {
    private final TableView<Entry> table = new TableView<>();
    private final FilteredList<Entry> rows;
    private final SplitPane split = new SplitPane();
    private final BorderPane sidebar = new BorderPane();
    private final VBox details = new VBox(8);
    private final TextArea payload = new TextArea();
    private final SplitPane detailView = new SplitPane();
    private final TextField search = new TextField();
    private final ComboBox<String> types = new ComboBox<>();
    private final ComboBox<RecipeFilterMode> sourceMode = new ComboBox<>(FXCollections.observableArrayList(RecipeFilterMode.values()));
    private final ComboBox<String> sourceValue = new ComboBox<>();
    private RecipeEditorSidebar draft;
    private boolean runtimeOnly, restoringSelection;
    private double divider = 0.57;
    private List<dev.recipeeditor.integrations.gregtech.GregTechMachineCatalog.Machine> machines=List.of();

    public RuntimeRecipeBrowserView(final List<Entry> entries) {
        this(entries, null, recipe -> false, null, entry -> false);
    }

    RuntimeRecipeBrowserView(final List<Entry> entries, final Consumer<RuntimeData.Recipe> copy,
            final Predicate<RuntimeData.Recipe> canCopy, final Consumer<Entry> edit, final Predicate<Entry> canEdit) {
        rows = new FilteredList<>(FXCollections.observableArrayList(entries));
        table.setId("runtime-recipe-table");
        sidebar.setId("runtime-recipe-sidebar");
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Recipe ID", row -> row.id().toString(), 260));
        table.getColumns().add(column("Type", this::type, 185));
        table.getColumns().add(column("Runtime status", row -> row.runtime()==null ? "Static" : row.runtime().status().name(), 90));

        search.setPromptText("Search ID, type or serializer");
        search.textProperty().addListener((o,a,b) -> filter());
        types.getItems().add("All types");
        entries.stream().map(this::type).distinct().sorted().forEach(types.getItems()::add);
        types.getSelectionModel().selectFirst();
        types.valueProperty().addListener((o,a,b) -> filter());
        sourceMode.setValue(RecipeFilterMode.SOURCE_MOD);
        populateSources(entries);
        sourceMode.valueProperty().addListener((o,a,b) -> populateSources(entries));
        sourceValue.valueProperty().addListener((o,a,b) -> filter());
        final var filters = new FlowPane(8, 5, search, sourceMode, sourceValue, types);

        final var close = new Button("Close sidebar");
        close.setOnAction(event -> hideSidebar());
        final var header = new HBox(8, new Label("Recipe details"), close);
        header.getStyleClass().add("view-header");
        sidebar.setTop(header);
        sidebar.setMinWidth(280);
        payload.setEditable(false);
        final var scroll = new ScrollPane(details);
        scroll.setFitToWidth(true);
        detailView.getItems().setAll(scroll,payload);
        detailView.setOrientation(javafx.geometry.Orientation.VERTICAL);
        sidebar.setCenter(detailView);
        final var show = new Button("Show recipe details");
        show.setOnAction(event -> { if(table.getSelectionModel().getSelectedItem()!=null) showSidebar(); });
        setTop(new VBox(8, filters, show));
        split.getItems().setAll(table);
        setCenter(split);
        table.getSelectionModel().selectedItemProperty().addListener((o, previous, row) -> {
            if(restoringSelection) return;
            if(!allowLeave()) {
                restoringSelection = true;
                try { table.getSelectionModel().select(previous); }
                finally { restoringSelection = false; }
                return;
            }
            clearDraft();
            if(row==null) {
                details.getChildren().clear();
                payload.clear();
                return;
            }
            final var summary = new Label(row.id() + "\n" + type(row) + "\n" + row.provenance().describe());
            summary.setWrapText(true);
            details.getChildren().setAll(summary);
            if(edit!=null) {
                final var button = new Button("Edit project definition");
                button.setDisable(!canEdit.test(row));
                button.setOnAction(event -> edit.accept(row));
                details.getChildren().add(button);
            }
            if(copy!=null && row.runtime()!=null) {
                final var button = new Button("Copy to New Project Recipe…");
                button.setDisable(!canCopy.test(row.runtime()));
                button.setOnAction(event -> copy.accept(row.runtime()));
                details.getChildren().add(button);
            }
            if(row.runtime()!=null) {
                final var runtime = row.runtime();
                final var evidence = new Label("Serializer: "+runtime.serializer()+"\nAdapter: "+runtime.adapter()
                        +"\nSource: "+runtime.sourceInformation()+"\n"+String.join("\n",runtime.diagnostics()));
                evidence.setWrapText(true);
                details.getChildren().add(evidence);
                if(machines.stream().anyMatch(machine->machine.id().toString().equals(runtime.type()))) {
                    details.getChildren().add(new GregTechMachineSelector(machines,
                            dev.recipeeditor.core.ResourceLocation.parse(runtime.type()),machine->{},false));
                }
                payload.setText(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(runtime.properties()));
            } else {
                payload.setText(row.staticDefinitions().getFirst().rawJson());
            }
            final var diagnostics = new Label(String.join("\n",row.diagnostics()));
            diagnostics.setWrapText(true);
            details.getChildren().add(diagnostics);
            showSidebar();
        });
    }

    void machines(final List<dev.recipeeditor.integrations.gregtech.GregTechMachineCatalog.Machine> values) {
        machines=List.copyOf(values);
    }

    boolean allowLeave() { return draft==null || draft.allowClose(); }

    void openDraft(final RecipeEditorSidebar next) {
        clearDraft();
        draft = next;
        sidebar.setCenter(next);
        showSidebar();
    }

    void hideSidebar() {
        if(!allowLeave()) return;
        clearDraft();
        final double[] positions = split.getDividerPositions();
        if(positions.length>0) divider = positions[0];
        split.getItems().remove(sidebar);
    }

    private void showSidebar() {
        if(!split.getItems().contains(sidebar)) {
            split.getItems().add(sidebar);
            split.setDividerPositions(divider);
        }
    }

    private void clearDraft() {
        if(draft==null) return;
        draft.close();
        draft = null;
        sidebar.setCenter(detailView);
    }

    @Override public void close() { clearDraft(); }

    public void runtimeOnly(final boolean value) {
        if(runtimeOnly!=value) { runtimeOnly=value; filter(); }
    }

    private void populateSources(final List<Entry> entries) {
        sourceValue.getItems().setAll("All");
        if(sourceMode.getValue()==RecipeFilterMode.RECIPE_NAMESPACE) {
            entries.stream().map(row -> row.id().namespace()).distinct().sorted().forEach(sourceValue.getItems()::add);
        } else {
            sourceValue.getItems().add("UNKNOWN");
            entries.stream().flatMap(row -> row.provenance().sourceModIds().stream()).distinct().sorted().forEach(sourceValue.getItems()::add);
        }
        sourceValue.setValue("All");
    }

    private String type(final Entry row) {
        return row.runtime()==null ? row.staticDefinitions().getFirst().type().displayId() : row.runtime().type();
    }

    private void filter() {
        final String query=search.getText().strip().toLowerCase(Locale.ROOT);
        rows.setPredicate(row -> (!runtimeOnly||row.runtime()!=null) && matchesSource(row)
                && ("All types".equals(types.getValue()) || Objects.equals(type(row),types.getValue()))
                && (row.id().toString().contains(query)||type(row).contains(query)
                || (row.runtime()!=null && row.runtime().serializer().contains(query))));
    }

    private boolean matchesSource(final Entry row) {
        final String selected=sourceValue.getValue();
        if(selected==null||selected.equals("All")) return true;
        if(sourceMode.getValue()==RecipeFilterMode.RECIPE_NAMESPACE) return row.id().namespace().equals(selected);
        return row.provenance().sourceModIds().contains(selected)
                || (selected.equals("UNKNOWN") && row.provenance().sourceModIds().isEmpty());
    }

    private TableColumn<Entry,String> column(final String title,final Function<Entry,String> text,final int width) {
        final var column=new TableColumn<Entry,String>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
