package dev.recipeeditor.registry;

/** Version capability: other releases can provide another layout without changing scanners. */
public record ResourceLayout(String recipesDirectory) {
    public static ResourceLayout minecraft1201() { return new ResourceLayout("recipes"); }
}
