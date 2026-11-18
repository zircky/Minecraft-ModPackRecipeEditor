package dev.recipeeditor.integrations.api;
import java.util.List;
import dev.recipeeditor.recipeengine.editor.ValidationIssue;
public interface IntegrationRecipeValidator<T> {
    List<ValidationIssue> validate(T recipe);
}
