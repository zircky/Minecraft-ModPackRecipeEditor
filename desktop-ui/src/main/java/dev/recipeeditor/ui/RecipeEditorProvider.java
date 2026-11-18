package dev.recipeeditor.ui;
import dev.recipeeditor.recipeengine.RecipeDefinition;
import dev.recipeeditor.integrations.api.IntegrationContext;
import javafx.scene.Node;
public interface RecipeEditorProvider {
    boolean supports(RecipeDefinition recipe, IntegrationContext integration);
    Node createEditor(RecipeEditorContext context);
}
