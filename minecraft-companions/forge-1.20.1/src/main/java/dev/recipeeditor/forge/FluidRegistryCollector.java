package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.world.level.material.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public final class FluidRegistryCollector {
    public RuntimeData.Registry collect(final ResourceLocation id,final Fluid fluid) {
        final var metadata=new TreeMap<String,String>();metadata.put("namespace",id.getNamespace());metadata.put("scope","registered Fluid, not FluidStack");
        if(fluid instanceof FlowingFluid flowing){metadata.put("source",Boolean.toString(flowing.getSource()==fluid));metadata.put("flowing",Boolean.toString(flowing.getFlowing()==fluid));}
        if(fluid instanceof FlowingFluid flowing){metadata.put("sourceFluid",Objects.toString(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(flowing.getSource()),"UNKNOWN"));metadata.put("flowingFluid",Objects.toString(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(flowing.getFlowing()),"UNKNOWN"));}
        final var name=fluid.getFluidType().getDescription();metadata.put("nameComponent",net.minecraft.network.chat.Component.Serializer.toJson(name));
        if(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated)metadata.put("localizationKey",translated.getKey());
        return new RuntimeData.Registry(RuntimeData.Kind.FLUID,id.toString(),fluid.getFluidType().getDescription().getString(),fluid.getClass().getName(),metadata,Map.of());
    }
}
