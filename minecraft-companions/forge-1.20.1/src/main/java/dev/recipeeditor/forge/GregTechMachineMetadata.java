package dev.recipeeditor.forge;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/** Reads declared slot limits from public GTCEu registry objects when the ABI is known. */
final class GregTechMachineMetadata {
    Map<String,String> read(final Object type) {
        final var mod=ModList.get().getModContainerById("gtceu");
        if(mod.isEmpty())return Map.of();
        final String version=mod.orElseThrow().getModInfo().getVersion().toString();
        final boolean snapshot8=version.equals("8.0.0-SNAPSHOT+e24b238");
        if(!snapshot8&&!version.equals("7.1.4"))return Map.of();
        try {
            final var recipeType=Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeType");
            if(!recipeType.isInstance(type))return Map.of();
            final var capability=Class.forName("com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability");
            final var identity=capability.getField(snapshot8?"registryName":"name");
            final var metadata=new TreeMap<String,String>();
            for(final var direction:Map.of("inputs","maxInputs","outputs","maxOutputs").entrySet()) {
                final var limits=(Map<?,?>)recipeType.getField(direction.getValue()).get(type);
                for(final var limit:limits.entrySet()) {
                    final var key=identity.get(limit.getKey());
                    final String name=key instanceof ResourceLocation id
                            ? id.getNamespace().equals("gtceu")?id.getPath():id.toString() : (String)key;
                    final int count=((Number)limit.getValue()).intValue();
                    if(name!=null&&count>=0)metadata.put("gt.limit."+direction.getKey()+"."+name,Integer.toString(count));
                }
            }
            metadata.put("gt.machineVersion",version);
            return metadata;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            return Map.of();
        }
    }
}
