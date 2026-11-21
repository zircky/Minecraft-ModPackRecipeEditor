package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

/** A material is not an Item; its forms link only to confirmed registry entries. */
public record MaterialDefinition(ResourceLocation id, String name, String localizationKey, Map<String,String> localizedNames,
        Map<String,String> properties, List<String> flags, List<Form> forms, Map<String,String> metadata) {
    public record Form(String prefix,ResourceKind kind,ResourceLocation resourceId) { }
    public MaterialDefinition { localizedNames=Map.copyOf(localizedNames);properties=Map.copyOf(properties);flags=List.copyOf(flags);forms=List.copyOf(forms);metadata=Map.copyOf(metadata); }
}
