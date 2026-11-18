package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.recipeengine.RecipeDefinition;
import java.util.List;
public record RecipeValidationResult(RecipeDefinition recipe,List<ValidationIssue> issues) {
    public RecipeValidationResult { issues=List.copyOf(issues); }
    public boolean valid() { return issues.stream().noneMatch(issue->issue.severity()==ValidationIssue.Severity.ERROR); }
}
