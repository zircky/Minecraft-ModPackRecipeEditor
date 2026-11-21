package dev.recipeeditor.integrations.api;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

public record ModdedRecipeType(ResourceLocation id,Map<String,String> names,Map<String,String> machines) {
    public ModdedRecipeType {Objects.requireNonNull(id);names=Map.copyOf(names);machines=Map.copyOf(machines);}
}
