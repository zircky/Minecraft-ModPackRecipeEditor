package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.util.*;
public record InventoryItemModel(ResourceLocation itemId,Kind kind,BlockGeometryModel geometry,ResourceVisual layers,GuiDisplayTransform gui,String guiLight,List<ResourceSource> sources,ObjItemMesh obj) {
    public enum Kind{GEOMETRY,GENERATED_2D,OBJ}
    public InventoryItemModel(final ResourceLocation itemId,final Kind kind,final BlockGeometryModel geometry,final ResourceVisual layers,
            final GuiDisplayTransform gui,final String guiLight,final List<ResourceSource> sources){this(itemId,kind,geometry,layers,gui,guiLight,sources,null);}
    public InventoryItemModel{Objects.requireNonNull(itemId);Objects.requireNonNull(kind);Objects.requireNonNull(gui);sources=List.copyOf(sources);switch(kind){case GEOMETRY->Objects.requireNonNull(geometry);case GENERATED_2D->Objects.requireNonNull(layers);case OBJ->Objects.requireNonNull(obj);}}
}
