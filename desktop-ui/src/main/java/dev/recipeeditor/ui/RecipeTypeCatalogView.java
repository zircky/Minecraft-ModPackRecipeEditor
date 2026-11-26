package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.RecipeTypeRegistry.Descriptor;
import dev.recipeeditor.companionclient.RecipeTypeRegistry.Operation;
import java.util.*;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.*;

final class RecipeTypeCatalogView extends BorderPane {
    private final ObservableList<Descriptor> items = FXCollections.observableArrayList();
    private final FilteredList<Descriptor> rows = new FilteredList<>(items);
    private final TableView<Descriptor> table = new TableView<>(rows);
    private final TextField search = new TextField();
    private final CheckBox canCreate = new CheckBox("Can create");
    private final Label details = new Label("Load or synchronize a runtime snapshot.");
    private final MenuButton create = new MenuButton("Create Recipe");
    private java.util.function.Consumer<String> createRecipe;

    RecipeTypeCatalogView() {
        search.setPromptText("Search type ID or serializer");
        search.textProperty().addListener((obs, previous, value) -> filter());
        canCreate.selectedProperty().addListener((obs, previous, value) -> filter());
        create.setDisable(true);
        setTop(new HBox(12, search, canCreate, create));
        table.getColumns().add(column("Type ID", Descriptor::type));
        table.getColumns().add(column("Namespace", Descriptor::namespace));
        table.getColumns().add(column("Recipes", value -> Integer.toString(value.recipes())));
        table.getColumns().add(column("Serializers", value -> String.join(", ", new TreeSet<>(value.serializers()))));
        table.getColumns().add(column("Available", value -> new TreeSet<>(value.capabilities()).toString()));
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, value) -> {
            updateCreate();
            details.setText(value == null ? "Select a recipe type." : value.reason()
                    + "\nDeclared source mod: " + value.sourceMod()
                    + " · Namespace mod version: " + value.namespaceModVersion()
                    + "\nRegistered type: " + value.registered()
                    + "\nObserved properties: " + new TreeSet<>(value.properties()));
        });
        details.setWrapText(true);
        setCenter(table);
        setBottom(details);
    }

    void show(final List<Descriptor> types) {
        items.setAll(types);
    }

    void createWith(final java.util.function.Consumer<String> action) {
        createRecipe = action;
        updateCreate();
    }

    private void updateCreate() {
        create.getItems().clear();
        final var type = table.getSelectionModel().getSelectedItem();
        if (createRecipe != null && type != null) {
            new dev.recipeeditor.companionclient.RecipeTypeRegistry().creationSerializers(type).forEach(serializer -> {
                final var item = new MenuItem(serializer);
                item.setOnAction(event -> {
                    if (createRecipe != null) createRecipe.accept(serializer);
                });
                create.getItems().add(item);
            });
        }
        create.setDisable(create.getItems().isEmpty());
    }

    private void filter() {
        final String query = search.getText().strip().toLowerCase(Locale.ROOT);
        rows.setPredicate(type -> (!canCreate.isSelected() || type.capabilities().contains(Operation.CREATE))
                && (type.type().contains(query) || type.serializers().stream().anyMatch(id -> id.contains(query))));
    }

    private TableColumn<Descriptor, String> column(final String title, final Function<Descriptor,String> text) {
        final var column = new TableColumn<Descriptor,String>(title);
        column.setPrefWidth(220);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue())));
        return column;
    }
}
