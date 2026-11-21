package dev.recipeeditor.ui;
import java.util.*;

/** Immutable presentation data; this model has no JavaFX types or mutable game objects. */
public record RecipeLayoutModel(String recipeType,RecipeLayoutProvider.Kind kind,List<Input> inputs,
        RecipeSlotContent output,boolean dynamicOutput,Map<String,String> parameters) {
    public record Input(String role,int index,String json){ }
    public RecipeLayoutModel{Objects.requireNonNull(recipeType);Objects.requireNonNull(kind);inputs=List.copyOf(inputs);Objects.requireNonNull(output);parameters=Collections.unmodifiableMap(new LinkedHashMap<>(parameters));}
}
