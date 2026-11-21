package dev.recipeeditor.forge;

import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Optional read-only public GT8 API. No generated IDs, private access, recipe-adapter activation or writes. */
public final class GregTechMaterialCollector {
    private final Map<String,List<RuntimeData.MaterialForm>> forms=new HashMap<>();
    private Class<?> materialClass,keyClass;
    private Method materialId,entryLookup,entryMaterial,entryPrefix,prefixId;
    public List<Runnable> jobs(final Consumer<RuntimeData.Material> output,final List<String> diagnostics) {
        if(!ModList.get().isLoaded("gtceu")) { diagnostics.add("Materials: GTCEu is not installed");return List.of(); }
        final String version=ModList.get().getModContainerById("gtceu").orElseThrow().getModInfo().getVersion().toString();
        if(!version.equals("8.0.0-SNAPSHOT+e24b238")) { diagnostics.add("Materials: unsupported GTCEu ABI "+version+"; verified build is 8.0.0-SNAPSHOT+e24b238");return List.of(); }
        try {
            initialize();
            final Object registry=Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries").getField("MATERIALS").get(null);
            final var jobs=new ArrayList<Runnable>();
            ForgeRegistries.ITEMS.forEach(item->jobs.add(()->link(item,RuntimeData.Kind.ITEM,ForgeRegistries.ITEMS.getKey(item).toString(),diagnostics)));
            ForgeRegistries.BLOCKS.forEach(block->jobs.add(()->link(block,RuntimeData.Kind.BLOCK,ForgeRegistries.BLOCKS.getKey(block).toString(),diagnostics)));
            for(final Object material:(Iterable<?>)registry) jobs.add(()->{
                try { output.accept(collect(material)); }
                catch(ReflectiveOperationException|RuntimeException failure) { diagnostic(diagnostics,"Material conversion failed: "+failure.getClass().getSimpleName()); }
            });
            diagnostics.add("Materials: GT8 e24b238 public registry API; all forms checked against actual Forge registrations");
            return jobs;
        } catch(ReflectiveOperationException|LinkageError failure) {
            diagnostics.add("Materials: required GT public API unavailable: "+failure.getClass().getSimpleName());return List.of();
        }
    }
    private void initialize() throws ReflectiveOperationException {
        materialClass=Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.Material");
        keyClass=Class.forName("com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey");
        materialId=materialClass.getMethod("getID");
        final var entry=Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry");
        entryLookup=Class.forName("com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper").getMethod("getMaterialEntry",ItemLike.class);
        entryMaterial=entry.getMethod("material");entryPrefix=entry.getMethod("tagPrefix");
        prefixId=Class.forName("com.gregtechceu.gtceu.api.data.tag.TagPrefix").getMethod("id");
    }
    private void link(final ItemLike item,final RuntimeData.Kind kind,final String resource,final List<String> diagnostics) {
        if(resource.equals("minecraft:air"))return;
        try {
            final Object entry=entryLookup.invoke(null,item);
            if(entry==null)return;
            final Object material=entryMaterial.invoke(entry),prefix=entryPrefix.invoke(entry);
            if(material==null||prefix==null)return;
            final String id=materialId.invoke(material).toString();
            forms.computeIfAbsent(id,k->new ArrayList<>()).add(new RuntimeData.MaterialForm(prefixId.invoke(prefix).toString(),kind,resource));
        } catch(ReflectiveOperationException|RuntimeException failure) { diagnostic(diagnostics,"Material item link unavailable: "+resource); }
    }
    private RuntimeData.Material collect(final Object material) throws ReflectiveOperationException {
        final String id=materialId.invoke(material).toString();
        final var properties=new TreeMap<String,String>();
        final Class<?> propertyKey=Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey");
        for(final var field:propertyKey.getFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==propertyKey) {
            final Object property=materialClass.getMethod("getProperty",propertyKey).invoke(material,field.get(null));
            if(property!=null) {
                final String propertyName=propertyKey.getMethod("getKey").invoke(field.get(null)).toString();
                properties.put(propertyName,property.getClass().getSimpleName());
                for(final var getter:property.getClass().getMethods()) {
                    final var type=getter.getReturnType();
                    if(getter.getParameterCount()!=0||Modifier.isStatic(getter.getModifiers())||!getter.getDeclaringClass().getPackageName().equals("com.gregtechceu.gtceu.api.data.chemical.material.properties"))continue;
                    if(!(getter.getName().startsWith("get")||getter.getName().startsWith("is")))continue;
                    if(type.isPrimitive()||type==String.class||type.isEnum())properties.put(propertyName+"."+getter.getName(),Objects.toString(getter.invoke(property),"UNKNOWN"));
                }
            }
        }
        // GT8's public MaterialFlags.toString emits each actual flag followed by a newline, including addon flags.
        final var flags=Objects.toString(materialClass.getMethod("getFlags").invoke(material)).lines().map(String::strip).filter(value->!value.isEmpty()).toList();
        final var actualForms=new ArrayList<>(forms.getOrDefault(id,List.of()));
        if((boolean)materialClass.getMethod("hasFluid").invoke(material))for(final Object key:(Collection<?>)keyClass.getMethod("allKeys").invoke(null)) {
            final Object value=materialClass.getMethod("getFluid",keyClass).invoke(material,key);
            if(value instanceof Fluid fluid) {
                final String prefix="fluid/"+keyClass.getMethod("getResourceLocation").invoke(key);
                addFluid(actualForms,prefix,fluid);
                if(fluid instanceof FlowingFluid flowing)addFluid(actualForms,prefix+"/flowing",flowing.getFlowing());
            }
        }
        final var component=(Component)materialClass.getMethod("getLocalizedName").invoke(material);
        final String key=materialClass.getMethod("getUnlocalizedName").invoke(material).toString();
        final var metadata=new TreeMap<String,String>();metadata.put("adapter","gtceu-materials-8-e24b238");
        metadata.put("propertiesDescription",Objects.toString(materialClass.getMethod("getProperties").invoke(material)));
        metadata.put("flagsDescription",Objects.toString(materialClass.getMethod("getFlags").invoke(material)));
        metadata.put("formula",Objects.toString(materialClass.getMethod("getChemicalFormula").invoke(material),""));
        metadata.put("components",Objects.toString(materialClass.getMethod("getMaterialComponents").invoke(material)));
        metadata.put("nameComponent",Component.Serializer.toJson(component));
        return new RuntimeData.Material(id,component.getString(),key,Map.of(),properties,flags,actualForms.stream().distinct().toList(),metadata);
    }
    private void addFluid(final List<RuntimeData.MaterialForm> values,final String prefix,final Fluid fluid) {
        final var id=ForgeRegistries.FLUIDS.getKey(fluid);
        if(id!=null&&!id.toString().equals("minecraft:empty")&&ForgeRegistries.FLUIDS.getValue(id)==fluid)values.add(new RuntimeData.MaterialForm(prefix,RuntimeData.Kind.FLUID,id.toString()));
    }
    private void diagnostic(final List<String> diagnostics,final String message) { if(diagnostics.size()<256)diagnostics.add(message); }
}
