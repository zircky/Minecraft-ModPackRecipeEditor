package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public final class ItemRegistryCollector {
    public RuntimeData.Registry collect(final ResourceLocation id,final Item item) {
        return new RuntimeData.Registry(RuntimeData.Kind.ITEM,id.toString(),item.getDescription().getString(),item.getClass().getName(),Map.of("namespace",id.getNamespace(),"localizationKey",item.getDescriptionId(),"blockItem",Boolean.toString(item instanceof BlockItem),"scope","registered Item, not ItemStack variants"),Map.of());
    }
}
