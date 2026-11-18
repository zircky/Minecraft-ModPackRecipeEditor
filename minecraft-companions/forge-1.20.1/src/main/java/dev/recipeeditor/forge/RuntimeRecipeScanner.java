package dev.recipeeditor.forge;

import dev.recipeeditor.companionprotocol.ProtocolCodec;
import dev.recipeeditor.companionprotocol.RuntimeData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import java.util.*;
import java.util.function.Consumer;

public final class RuntimeRecipeScanner {
    public List<Runnable> jobs(final MinecraftServer server,final Consumer<RuntimeData.Recipe> output,final List<String> diagnostics) {
        final var adapters=new ArrayList<RuntimeRecipeAdapter>();
        final var recipes=new LinkedHashMap<String,Recipe<?>>();
        final var variants=new LinkedHashMap<String,List<Recipe<?>>>();
        final var managerIds=new HashSet<String>();
        server.getRecipeManager().getRecipes().forEach(recipe->{recipes.put(recipe.getId().toString(),recipe);managerIds.add(recipe.getId().toString());});
        if(ModList.get().isLoaded("gtceu")) {
            try {
                final var gt=new GregTechRuntimeRecipeAdapter();adapters.add(gt);
                for(final var recipe:gt.additionalRecipes()) {
                    final String id=recipe.getId().toString();final var old=recipes.putIfAbsent(id,recipe);
                    if(old!=null&&old!=recipe) {
                        final var alternatives=variants.computeIfAbsent(id,key->new ArrayList<>());
                        if(alternatives.stream().noneMatch(value->value==recipe))alternatives.add(recipe);
                        if(alternatives.size()>64)throw new IllegalStateException("Runtime variant count exceeds 64 for "+id);
                    }
                }
            }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
                diagnostics.add("GT enumeration unavailable: "+unavailable.getClass().getSimpleName()+" "+unavailable.getMessage());
            }
        }else diagnostics.add("GTCEu absent; GT runtime collection not performed");
        adapters.add(new VanillaRecipeAdapter());adapters.add(new GenericRecipeAdapter());
        variants.forEach((id,values)->diagnostics.add("Runtime ID "+id+" has "+values.size()+" additional category/proxy definitions; variants retained, machine winner not inferred"));
        final var jobs=new ArrayList<Runnable>();
        recipes.forEach((id,recipe)->jobs.add(()->{
            final var primary=convert(recipe,server,adapters);
            final var properties=primary.properties();final var notes=new ArrayList<>(primary.diagnostics());
            final var alternatives=new JsonArray();
            for(final var variant:variants.getOrDefault(id,List.of()))alternatives.add(ProtocolCodec.GSON.toJsonTree(convert(variant,server,adapters)));
            if(alternatives.size()>0){properties.add("runtimeVariants",alternatives);notes.add("Additional runtime variants are retained in properties.runtimeVariants; do not infer a universal winner from the primary ID.");}
            final String origin=(managerIds.contains(id)?"Server RecipeManager":"GTCEu category/proxy collection")+"; original pack/source UNKNOWN";
            output.accept(new RuntimeData.Recipe(primary.id(),primary.type(),primary.serializer(),primary.runtimeClass(),primary.adapter(),alternatives.size()>0?RuntimeData.Support.PARTIALLY_SUPPORTED:primary.status(),properties,origin,notes));
        }));
        return jobs;
    }
    private RuntimeData.Recipe convert(final Recipe<?> recipe,final MinecraftServer server,final List<RuntimeRecipeAdapter> adapters) {
        try{return adapters.stream().filter(a->a.supports(recipe)).findFirst().orElseThrow().convert(recipe,server.registryAccess());}
        catch(RuntimeException|LinkageError failed){return new GenericRecipeAdapter().metadata(recipe,"adapter-failure",RuntimeData.Support.PARTIALLY_SUPPORTED,new JsonObject(),List.of("Conversion failed: "+failed.getClass().getSimpleName()));}
    }
}
