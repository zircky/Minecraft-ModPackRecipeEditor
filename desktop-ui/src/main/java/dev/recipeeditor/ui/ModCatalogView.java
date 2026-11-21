package dev.recipeeditor.ui;

import dev.recipeeditor.core.importing.*;
import java.util.*;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.*;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Virtualized mod catalogue; receives immutable results and performs presentation-only filtering. */
public final class ModCatalogView extends VBox {
    private final Label summary = new Label();
    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> loaderFilter = new ComboBox<>();
    private final TableView<Row> table = new TableView<>();
    private final TextArea details = readOnlyText();
    private final TextArea diagnostics = readOnlyText();
    private final javafx.collections.ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> filtered = new FilteredList<>(rows);
    private ModpackImportResult result;

    public ModCatalogView() {
        setSpacing(14); getStyleClass().add("workspace-view");
        summary.getStyleClass().add("brand");
        summary.setWrapText(true);
        search.setPromptText("Search by mod name or ID");
        HBox.setHgrow(search,Priority.ALWAYS);
        statusFilter.getItems().add("All statuses");
        Arrays.stream(ScanStatus.values()).map(Enum::name).forEach(statusFilter.getItems()::add);
        statusFilter.getSelectionModel().selectFirst();
        loaderFilter.getItems().add("All loaders");
        Arrays.stream(ModLoaderType.values()).map(Enum::name).forEach(loaderFilter.getItems()::add);
        loaderFilter.getSelectionModel().selectFirst();
        search.textProperty().addListener((obs,oldValue,newValue) -> filter());
        statusFilter.valueProperty().addListener((obs,oldValue,newValue) -> filter());
        loaderFilter.valueProperty().addListener((obs,oldValue,newValue) -> filter());
        configureTable();
        final var sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        final var split = new SplitPane(table,createDetailTabs());
        split.setDividerPositions(0.62);
        VBox.setVgrow(split,Priority.ALWAYS);
        final var filters=new FlowPane(search,statusFilter,loaderFilter);filters.getStyleClass().add("filter-row");
        getChildren().addAll(summary,filters,split);
    }
    private void configureTable() {
        table.getColumns().add(column("Name",Row::name,190));
        table.getColumns().add(column("Mod ID",Row::id,150));
        table.getColumns().add(column("Version",Row::version,120));
        table.getColumns().add(column("Loader",Row::loader,100));
        table.getColumns().add(column("Status",Row::status,100));
        table.setPlaceholder(new EmptyStateView("No matching mods","Change the search, status or loader filter. If the instance has no mod JARs, check its mods directory."));
        table.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,row) -> describe(row));
    }
    private TableColumn<Row,String> column(final String title, final Function<Row,String> value, final int width) {
        final var column = new TableColumn<Row,String>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }
    private TabPane createDetailTabs() {
        final var info = new Tab("Mod details",details); info.setClosable(false);
        final var issues = new Tab("Import diagnostics",diagnostics); issues.setClosable(false);
        return new TabPane(info,issues);
    }
    private TextArea readOnlyText() {
        final var area = new TextArea(); area.setEditable(false); area.setWrapText(true); return area;
    }
    public void show(final ModpackImportResult result) {
        this.result = Objects.requireNonNull(result);
        final var pack = result.modpack();
        summary.setText("Minecraft: " + pack.minecraftVersion().value() + "   |   Loader: " + pack.loader().type()
                + " " + pack.loader().version() + "   |   Mod definitions: " + pack.mods().size() + "   |   JARs: " + pack.jars().size());
        final var rows = new ArrayList<Row>();
        for (final var jar : pack.jars()) {
            if (jar.mods().isEmpty()) rows.add(new Row(jar,Optional.empty()));
            else jar.mods().forEach(mod -> rows.add(new Row(jar,Optional.of(mod))));
        }
        this.rows.setAll(rows);
        diagnostics.setText("Directory: " + pack.selectedDirectory() + "\nStructure: " + pack.structure()
                + "\nMinecraft sources: " + pack.minecraftVersion().sources() + "\nLoader sources: " + pack.loader().sources()
                + "\n\n" + formatDiagnostics(result.diagnostics()));
        if (!table.getItems().isEmpty()) table.getSelectionModel().selectFirst();
        else details.setText("No JAR files were found in the mods directory.");
    }
    private void filter() {
        final String query = search.getText().toLowerCase(Locale.ROOT).strip();
        filtered.setPredicate(row -> (row.name().toLowerCase(Locale.ROOT).contains(query) || row.id().toLowerCase(Locale.ROOT).contains(query))
                && (statusFilter.getSelectionModel().getSelectedIndex() == 0 || row.status().equals(statusFilter.getValue()))
                && (loaderFilter.getSelectionModel().getSelectedIndex() == 0 || row.loader().equals(loaderFilter.getValue())));
    }
    private void describe(final Row row) {
        if (row == null) { details.clear(); return; }
        final var text = new StringBuilder("JAR: ").append(row.jar().sourceJar()).append("\nStatus: ").append(row.status()).append("\n");
        row.mod().ifPresent(mod -> {
            final var m = mod.metadata();
            text.append("\nName: ").append(m.displayName()).append("\nID: ").append(m.id()).append("\nVersion: ").append(m.version())
                .append("\nLoader compatibility: ").append(m.loaderCompatibility()).append("\nAuthors: ").append(String.join(", ",m.authors()))
                .append("\nMetadata: ").append(m.metadataSource()).append("\n\n").append(m.description()).append("\n\nDependencies:\n");
            m.dependencies().forEach(d -> text.append(d.kind()).append(" · ").append(d.targetId()).append(" · ").append(d.versionConstraint()).append("\n"));
            if (result != null) text.append("\nDependency diagnostics:\n").append(formatDiagnostics(result.diagnostics().stream()
                    .filter(d -> d.source().startsWith(m.id() + " -> ")).toList()));
            text.append("\nRaw metadata:\n").append(m.rawMetadata());
        });
        text.append("\n\nJAR diagnostics:\n").append(formatDiagnostics(row.jar().diagnostics()));
        details.setText(text.toString()); details.positionCaret(0);
    }
    private String formatDiagnostics(final List<ImportDiagnostic> values) {
        return values.stream().map(d -> d.severity() + " · " + d.source() + "\n" + d.message()).collect(java.util.stream.Collectors.joining("\n\n"));
    }
    private record Row(ModScanResult jar, Optional<ModDefinition> mod) {
        String name() { return mod.map(m -> m.metadata().displayName()).orElse("[Unidentified JAR] " + jar.sourceJar().getFileName()); }
        String id() { return mod.map(m -> m.metadata().id()).orElse(""); }
        String version() { return mod.map(m -> m.metadata().version()).orElse("UNKNOWN"); }
        String loader() { return mod.map(m -> m.metadata().loaderCompatibility().name()).orElse("UNKNOWN"); }
        String status() { return jar.status().name(); }
    }
}
