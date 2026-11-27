package dev.recipeeditor.ui;

import dev.recipeeditor.integrations.api.IntegrationContext;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.recipeengine.editor.RecipeEditSession;
import dev.recipeeditor.recipeengine.editor.RecipeEditorService;
import javafx.scene.control.*;
import javafx.stage.Window;

/** Compatibility host for callers outside the inline recipe browser. */
public final class RecipeEditorDialog extends Dialog<Void> {
    public RecipeEditorDialog(final Window owner, final DiscoveryResult discovery,
            final RecipeEditorService editor, final EditorTasks tasks, final RecipeEditSession session,
            final boolean creating, final IntegrationContext integration) {
        final var sidebar = new RecipeEditorSidebar(discovery, editor, tasks, session, creating, integration);
        initOwner(owner);
        new UiTheme().apply(getDialogPane());
        setTitle("Recipe Editor - " + session.id());
        setResizable(true);
        getDialogPane().setPrefSize(1050, 760);
        getDialogPane().setContent(sidebar);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        setOnCloseRequest(event -> { if (!sidebar.allowClose()) event.consume(); });
        setOnHidden(event -> sidebar.close());
    }
}
