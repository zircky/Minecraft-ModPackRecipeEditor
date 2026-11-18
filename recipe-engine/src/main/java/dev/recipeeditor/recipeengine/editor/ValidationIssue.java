package dev.recipeeditor.recipeengine.editor;

public record ValidationIssue(Severity severity,String field,String message) {
    public enum Severity { ERROR, WARNING }
}
