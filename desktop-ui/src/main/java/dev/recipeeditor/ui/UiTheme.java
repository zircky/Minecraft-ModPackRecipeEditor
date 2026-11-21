package dev.recipeeditor.ui;

import java.util.Objects;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Dialog;

/** Shared installation also covers dialogs without a themed owner. */
public final class UiTheme {
    private final String stylesheet=Objects.requireNonNull(UiTheme.class.getResource("theme.css")).toExternalForm();
    public void apply(final Scene scene) {
        if(!scene.getStylesheets().contains(stylesheet))scene.getStylesheets().add(stylesheet);
    }
    public void apply(final DialogPane pane) {
        if(!pane.getStylesheets().contains(stylesheet))pane.getStylesheets().add(stylesheet);
    }
    public <D extends Dialog<?>> D apply(final D dialog) {
        apply(dialog.getDialogPane());
        return dialog;
    }
}
