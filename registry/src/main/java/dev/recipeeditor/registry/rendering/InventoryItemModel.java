package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.util.*;
public record InventoryItemModel(ResourceLocation itemId,Kind kind,BlockGeometryModel geometry,ResourceVisual layers,GuiDisplayTransform gui,String guiLight,List<ResourceSource> sources) {
    public enum Kind{GEOMETRY,GENERATED_2D}
    public InventoryItemModel{Objects.requireNonNull(itemId);Objects.requireNonNull(kind);Objects.requireNonNull(gui);sources=List.copyOf(sources);if(kind==Kind.GEOMETRY)Objects.requireNonNull(geometry);else Objects.requireNonNull(layers);}
}
