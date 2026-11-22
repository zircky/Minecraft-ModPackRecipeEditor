package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public final class ItemRegistryCollector {
    public RuntimeData.Registry collect(final ResourceLocation id,final Item item) {
        final var metadata=new HashMap<String,String>();metadata.put("namespace",id.getNamespace());metadata.put("localizationKey",item.getDescriptionId());metadata.put("blockItem",Boolean.toString(item instanceof BlockItem));metadata.put("scope","registered Item, not ItemStack variants");
        if(item instanceof BlockItem blockItem){final var blockId=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());if(blockId!=null)metadata.put("blockId",blockId.toString());}
        return new RuntimeData.Registry(RuntimeData.Kind.ITEM,id.toString(),item.getDescription().getString(),item.getClass().getName(),metadata,Map.of());
    }
}
