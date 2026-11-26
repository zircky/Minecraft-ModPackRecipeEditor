package dev.recipeeditor.forge;

import dev.recipeeditor.companionprotocol.RuntimeData;
import com.google.gson.*;
import com.mojang.serialization.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** Public read APIs for two inspected GTCEu builds. No setAccessible, writes or network-selected class names.
 * Reflection isolates the optional mod from class linking and keeps the base JAR loadable without GTCEu.
 */
public final class GregTechRuntimeRecipeAdapter implements RuntimeRecipeAdapter {
    private final Class<?> recipeClass,typeClass,categoryClass;
    private final Codec<Object> codec;
    private final Object registry;
    private final boolean snapshot8;
    private final String adapterId;
    private final Map<String,java.lang.reflect.Field> roleFields=new LinkedHashMap<>();
    private final java.lang.reflect.Field chance,maxChance,tierChanceBoost,capabilityName;
    @SuppressWarnings("unchecked") public GregTechRuntimeRecipeAdapter()throws ReflectiveOperationException {
        final String version=ModList.get().getModContainerById("gtceu").orElseThrow().getModInfo().getVersion().toString();
        snapshot8=version.equals("8.0.0-SNAPSHOT+e24b238");
        if(!snapshot8&&!version.equals("7.1.4"))throw new IllegalArgumentException("No inspected GTCEu public codec profile for "+version);
        adapterId=snapshot8?"gtceu-8.0-e24b238":"gtceu-7.1.4";
        recipeClass=Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipe");typeClass=Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeType");categoryClass=Class.forName("com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory");
        codec=(Codec<Object>)Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer").getField("CODEC").get(null);
        if(snapshot8) {
            chance=null;maxChance=null;tierChanceBoost=null;capabilityName=null;
        } else {
            for(final String role:List.of("inputs","outputs","tickInputs","tickOutputs"))roleFields.put(role,recipeClass.getField(role));
            final var contentClass=Class.forName("com.gregtechceu.gtceu.api.recipe.content.Content");chance=contentClass.getField("chance");maxChance=contentClass.getField("maxChance");tierChanceBoost=contentClass.getField("tierChanceBoost");capabilityName=Class.forName("com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability").getField("name");
        }
        registry=Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries").getField("RECIPE_TYPES").get(null);
    }
    @Override public boolean supports(final Recipe<?> recipe){return recipeClass.isInstance(recipe);}
    public Collection<Recipe<?>> additionalRecipes()throws ReflectiveOperationException {
        final var result=new ArrayList<Recipe<?>>();for(final var type:(Iterable<?>)registry) {
            for(final var category:(Set<?>)typeClass.getMethod("getCategories").invoke(type))for(final var recipe:(Set<?>)typeClass.getMethod("getRecipesInCategory",categoryClass).invoke(type,category))result.add((Recipe<?>)recipe);
            final var proxy=(Map<?,?>)typeClass.getMethod("getProxyRecipes").invoke(type);for(final var values:proxy.values())for(final var recipe:(List<?>)values)result.add((Recipe<?>)recipe);
        }return result;
    }
    private void annotateContents(final Object recipe,final JsonObject properties)throws IllegalAccessException {
        for(final var role:roleFields.entrySet()) {
            final var capabilities=(Map<?,?>)role.getValue().get(recipe);
            if(!properties.has(role.getKey()))continue;
            for(final var entry:capabilities.entrySet()) {
                final String name=capabilityName.get(entry.getKey()).toString();
                if(!properties.getAsJsonObject(role.getKey()).has(name))continue;
                final var encoded=properties.getAsJsonObject(role.getKey()).getAsJsonArray(name);final var original=(List<?>)entry.getValue();
                if(encoded.size()!=original.size())throw new IllegalStateException("GT content cardinality mismatch");
                for(int index=0;index<original.size();index++) {
                    final Object content=original.get(index);final var json=encoded.get(index).getAsJsonObject();
                    json.addProperty("chance",chance.getInt(content));json.addProperty("maxChance",maxChance.getInt(content));json.addProperty("tierChanceBoost",tierChanceBoost.getInt(content));
                    if(role.getKey().endsWith("Inputs")||role.getKey().equals("inputs"))json.addProperty("nonConsumableInput",chance.getInt(content)==0);
                }
            }
        }
    }
    private List<String> messages(final List<String> errors,final String explanation){final var result=new ArrayList<>(errors);result.add(explanation);return result;}
    @Override public RuntimeData.Recipe convert(final Recipe<?> recipe,final RegistryAccess registries) {
        if(snapshot8)return inspectSnapshot8(recipe,registries);
        try {
            final var conversionDiagnostics=new ArrayList<String>();
            final var encoded=codec.encodeStart(JsonOps.INSTANCE,recipe).resultOrPartial(conversionDiagnostics::add);
            if(encoded.isEmpty())return new GenericRecipeAdapter().metadata(recipe,"gtceu-7.1.4",RuntimeData.Support.PARTIALLY_SUPPORTED,new JsonObject(),List.of("GT codec could not represent all loaded properties"));
            final var properties=encoded.get().getAsJsonObject().deepCopy();annotateContents(recipe,properties);properties.addProperty("sourceJsonAvailable",false);
            final var category=recipeClass.getField("recipeCategory").get(recipe);if(category!=null)properties.addProperty("recipeCategory",categoryClass.getField("registryKey").get(category).toString());
            final Object inputEU=recipeClass.getMethod("getInputEUt").invoke(recipe),outputEU=recipeClass.getMethod("getOutputEUt").invoke(recipe);
            properties.addProperty("inputEUt",(Number)inputEU.getClass().getMethod("getTotalEU").invoke(inputEU));properties.addProperty("outputEUt",(Number)outputEU.getClass().getMethod("getTotalEU").invoke(outputEU));
            return new GenericRecipeAdapter().metadata(recipe,"gtceu-7.1.4",RuntimeData.Support.PARTIALLY_SUPPORTED,properties,messages(conversionDiagnostics,"GT codec state preserves item/fluid/tick contents, chance/maxChance/tierChanceBoost, conditions and data. It is not proof of original source or lossless builder reconstruction. chance=0 remains distinct; no flattening into ItemStacks."));
        }catch(ReflectiveOperationException|RuntimeException failure){return new GenericRecipeAdapter().metadata(recipe,"gtceu-7.1.4",RuntimeData.Support.PARTIALLY_SUPPORTED,new JsonObject(),List.of("GT conversion unavailable: "+failure.getClass().getSimpleName()));}
    }

    private RuntimeData.Recipe inspectSnapshot8(final Recipe<?> recipe,final RegistryAccess registries) {
        final var notes=new ArrayList<String>();
        final var encoded=codec.encodeStart(RegistryOps.create(JsonOps.INSTANCE,registries),recipe)
                .resultOrPartial(notes::add);
        final var properties=new JsonObject();
        if(notes.isEmpty()&&encoded.isPresent()&&encoded.get().isJsonObject()) {
            // Preserve the codec output as emitted, including chance logic, conditions and extra IO.
            properties.add("codecPayload",encoded.get());
        }
        notes.add("GTCEu 8 codec inspection only. No verified editor or export round trip for this build.");
        return new GenericRecipeAdapter().metadata(recipe,adapterId,RuntimeData.Support.PARTIALLY_SUPPORTED,properties,notes);
    }
}
