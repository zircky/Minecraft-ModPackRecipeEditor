package dev.recipeeditor.ui;
import dev.recipeeditor.recipeengine.RecipeDefinition;
import java.util.Map;
import java.util.function.BiConsumer;
public record RecipeEditorContext(RecipeDefinition recipe, Map<String,String> pending,
        BiConsumer<String,String> changed, BiConsumer<String,String> apply) { }
