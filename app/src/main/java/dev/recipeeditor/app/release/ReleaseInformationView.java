package dev.recipeeditor.app.release;

import dev.recipeeditor.core.ApplicationDirectories;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Minimal build identity and log access; updates/settings/full diagnostics are separate steps. */
public final class ReleaseInformationView {
    private final BuildMetadata metadata;
    private final Consumer<Path> openDirectory;

    public ReleaseInformationView(final BuildMetadata metadata, final Consumer<Path> openDirectory) {
        this.metadata = Objects.requireNonNull(metadata);
        this.openDirectory = Objects.requireNonNull(openDirectory);
    }

    public void attach(final Stage stage) {
        for (final int size : new int[]{16, 32, 48, 64, 128, 256}) stage.getIcons().add(icon(size));
        final var about = new Button("About");
        about.setOnAction(event -> showAbout(stage));
        final var logs = new Button("Open Logs Folder");
        logs.setOnAction(event -> openDirectory.accept(ApplicationDirectories.current().logs()));
        final var shell = (BorderPane) stage.getScene().getRoot();
        final var toolbar = shell.getTop();
        shell.setTop(null);
        shell.setTop(new VBox(toolbar, new ToolBar(about, logs)));
    }

    private void showAbout(final Stage stage) {
        aboutDialog(stage).showAndWait();
    }

    Alert aboutDialog(final Stage stage) {
        final var dialog = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.CLOSE);
        dialog.initOwner(stage);
        new dev.recipeeditor.ui.UiTheme().apply(dialog.getDialogPane());
        dialog.setTitle("About " + metadata.name());
        dialog.setHeaderText("Alpha build — stable release is NOT READY");
        dialog.setGraphic(new ImageView(icon(48)));
        final var details=new Label(metadata.diagnosticSummary());
        details.setWrapText(true);
        details.setMinHeight(Region.USE_PREF_SIZE);
        dialog.getDialogPane().setContent(details);
        dialog.getDialogPane().setPrefWidth(540);
        dialog.setResizable(true);
        return dialog;
    }

    private Image icon(final int size) {
        try (final var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/dev/recipeeditor/app/icons/app-" + size + ".png"))) {
            return new Image(input);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load bundled application icon", error); }
    }
}
