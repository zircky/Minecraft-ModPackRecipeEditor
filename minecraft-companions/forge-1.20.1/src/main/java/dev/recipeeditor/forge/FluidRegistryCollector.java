package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.world.level.material.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public final class FluidRegistryCollector {
    public RuntimeData.Registry collect(final ResourceLocation id,final Fluid fluid) {
        final var metadata=new TreeMap<String,String>();metadata.put("namespace",id.getNamespace());metadata.put("scope","registered Fluid, not FluidStack");
        if(fluid instanceof FlowingFluid flowing){metadata.put("source",Boolean.toString(flowing.getSource()==fluid));metadata.put("flowing",Boolean.toString(flowing.getFlowing()==fluid));}
        return new RuntimeData.Registry(RuntimeData.Kind.FLUID,id.toString(),fluid.getFluidType().getDescription().getString(),fluid.getClass().getName(),metadata,Map.of());
    }
}
