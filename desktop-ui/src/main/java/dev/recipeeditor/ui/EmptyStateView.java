package dev.recipeeditor.ui;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Explanatory empty content; optional actions must be backed by a real handler. */
public final class EmptyStateView extends VBox {
    public EmptyStateView(final String title,final String description) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        final var heading=new Label(Objects.requireNonNull(title));
        heading.getStyleClass().add("empty-title");
        heading.setWrapText(true);
        heading.setMaxWidth(480);
        final var body=new Label(Objects.requireNonNull(description));
        body.getStyleClass().add("muted");
        body.setWrapText(true);
        body.setMaxWidth(480);
        getChildren().addAll(heading,body);
    }
    public EmptyStateView(final String title,final String description,final String action,final Runnable handler) {
        this(title,description);
        Objects.requireNonNull(handler);
        final var button=new Button(Objects.requireNonNull(action));
        button.getStyleClass().add("primary-action");
        button.setOnAction(event->handler.run());
        getChildren().add(button);
    }
}
