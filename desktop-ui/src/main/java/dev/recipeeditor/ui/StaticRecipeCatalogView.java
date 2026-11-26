package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.RecipeDefinition;
import dev.recipeeditor.recipeengine.RecipeIndex;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Discovery view for formats without verified editing semantics. */
final class StaticRecipeCatalogView extends BorderPane {
    StaticRecipeCatalogView(final RecipeIndex index) {
        getStyleClass().add("workspace-view");
        final var search = new TextField();
        search.setPromptText("Search recipe ID or type");
        final var header = new VBox(8, new Label("Static recipes · read only"), search);
        header.getStyleClass().add("view-header");
        setTop(header);

        final var rows = new FilteredList<>(FXCollections.observableArrayList(index.recipes()));
        search.textProperty().addListener((observable, previous, value) -> {
            final String query = value.strip().toLowerCase(Locale.ROOT);
            rows.setPredicate(recipe -> recipe.id().toString().contains(query)
                    || recipe.type().displayId().toLowerCase(Locale.ROOT).contains(query));
        });
        final var table = new ListView<>(rows);
        table.setPlaceholder(new Label("No static recipe files found for this format."));
        table.setCellFactory(list -> new ListCell<RecipeDefinition>() {
            @Override protected void updateItem(final RecipeDefinition recipe, final boolean empty) {
                super.updateItem(recipe, empty);
                setText(empty ? null : recipe.id() + "  ·  " + recipe.type().displayId());
            }
        });
        final var source = new Label("Select a recipe to inspect its source.");
        source.setWrapText(true);
        final var payload = new TextArea();
        payload.setEditable(false);
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, recipe) -> {
            source.setText(recipe == null ? "Select a recipe to inspect its source."
                    : recipe.status() + " · " + recipe.source().archivePath() + " / " + recipe.source().internalPath());
            payload.setText(recipe == null ? "" : recipe.rawJson());
        });
        final var detail = new BorderPane(payload);
        detail.setTop(source);
        setCenter(new SplitPane(table, detail));
    }
}
