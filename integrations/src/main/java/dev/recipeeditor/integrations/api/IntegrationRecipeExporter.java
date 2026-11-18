package dev.recipeeditor.integrations.api;

public interface IntegrationRecipeExporter<T> {
    String generate(T recipe, String id, boolean replace);
}
