package dev.recipeeditor.recipeengine.editor;

/** Commands must restore their captured before-state even when execute fails partway through. */
public interface RecipeCommand { void execute(); void undo(); default long retainedBytes() { return 0; } }
