package dev.recipeeditor.integrations.api;

public interface RecipeEditing {
    String replace(String rawJson, String pointer, String jsonValue);
}
