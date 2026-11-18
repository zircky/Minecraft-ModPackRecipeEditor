package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.*;
public final class BlockRegistryCollector {
    public RuntimeData.Registry collect(final ResourceLocation id,final Block block) {
        final var properties=new TreeMap<String,List<String>>();for(final var property:block.getStateDefinition().getProperties())properties.put(property.getName(),values(property));
        return new RuntimeData.Registry(RuntimeData.Kind.BLOCK,id.toString(),block.getName().getString(),block.getClass().getName(),Map.of("namespace",id.getNamespace(),"localizationKey",block.getDescriptionId()),properties);
    }
    private <T extends Comparable<T>> List<String> values(final Property<T> property){return property.getPossibleValues().stream().map(property::getName).toList();}
}
