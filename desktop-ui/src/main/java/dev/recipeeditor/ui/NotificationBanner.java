package dev.recipeeditor.ui;

import java.util.Locale;
import java.util.Objects;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** One current operation outcome, not an unbounded notification feed. */
public final class NotificationBanner extends HBox {
    public enum Kind { SUCCESS, INFO, WARNING, ERROR }
    private final Label message=new Label();
    public NotificationBanner() {
        getStyleClass().add("notification-banner");
        message.setWrapText(true);
        message.setMinWidth(0);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setAccessibleRole(AccessibleRole.TEXT);
        HBox.setHgrow(message,Priority.ALWAYS);
        final var dismiss=new Button("Dismiss");
        dismiss.setAccessibleText("Dismiss notification");
        dismiss.setOnAction(event->dismiss());
        getChildren().addAll(message,dismiss);
        managedProperty().bind(visibleProperty());
        dismiss();
    }
    public void show(final Kind kind,final String text) {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(text);
        for(final var value:Kind.values())getStyleClass().remove("notification-"+value.name().toLowerCase(Locale.ROOT));
        getStyleClass().add("notification-"+kind.name().toLowerCase(Locale.ROOT));
        message.setText(kind+": "+text);
        setVisible(true);
    }
    public void dismiss() { setVisible(false); }
}
