package dev.recipeeditor.integrations.gregtech;

import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

public final class GregTechMachineCatalog {
    public record Machine(ResourceLocation id, String label, String sourceMod, String observedVersion,
                          Map<String,Integer> limits) {
        public Machine { limits=Map.copyOf(limits); }
        @Override public String toString() { return label; }
    }

    public List<Machine> load(final RuntimeData.Snapshot snapshot) {
        final var observed=new HashSet<String>();
        for(final var recipe:snapshot.recipes()) {
            if(!recipe.type().equals("UNKNOWN") && (recipe.adapter().startsWith("gtceu-")
                    || recipe.serializer().equals("gtceu:machine"))) observed.add(recipe.type());
        }
        final var result=new TreeMap<ResourceLocation,Machine>();
        snapshot.registries().stream().filter(row->row.kind()==RuntimeData.Kind.RECIPE_TYPE).forEach(row->{
            if(!observed.contains(row.id()) && !row.metadata().containsKey("gt.machineVersion")
                    && !row.runtimeClass().equals("com.gregtechceu.gtceu.api.recipe.GTRecipeType"))return;
            final var limits=new TreeMap<String,Integer>();
            row.metadata().forEach((key,value)->{
                if(key.startsWith("gt.limit."))try {
                    final int count=Integer.parseInt(value);
                    if(count>=0)limits.put(key.substring("gt.limit.".length()),count);
                } catch(NumberFormatException ignored) { /* No verified capacity for this entry. */ }
            });
            final var id=ResourceLocation.parse(row.id());
            result.put(id,new Machine(id,row.displayName().isBlank()?row.id():row.displayName(),
                    row.metadata().getOrDefault("sourceMod","UNKNOWN"),row.metadata().getOrDefault("gt.machineVersion","UNKNOWN"),limits));
        });
        for(final String value:observed) {
            final var id=ResourceLocation.parse(value);
            result.putIfAbsent(id,new Machine(id,value,"UNKNOWN","UNKNOWN",Map.of()));
        }
        return List.copyOf(result.values());
    }
}
