package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraftforge.registries.*;
import java.util.*;
import java.util.function.Consumer;
/** Builds work only on the server thread; each job converts a single registry entry. */
public final class RuntimeRegistryScanner {
    public List<Runnable> jobs(final Consumer<RuntimeData.Registry> output) {
        return jobs(output,Map.of());
    }
    public List<Runnable> jobs(final Consumer<RuntimeData.Registry> output,final Map<String,Map<String,String>> presentation) {
        final Consumer<RuntimeData.Registry> decorated=row->{
            final var metadata=new TreeMap<>(row.metadata());metadata.putAll(presentation.getOrDefault(row.kind()+":"+row.id(),Map.of()));
            output.accept(new RuntimeData.Registry(row.kind(),row.id(),row.displayName(),row.runtimeClass(),metadata,row.stateProperties()));
        };
        final var jobs=new ArrayList<Runnable>();
        ForgeRegistries.ITEMS.forEach(value->jobs.add(()->decorated.accept(new ItemRegistryCollector().collect(ForgeRegistries.ITEMS.getKey(value),value))));
        ForgeRegistries.BLOCKS.forEach(value->jobs.add(()->decorated.accept(new BlockRegistryCollector().collect(ForgeRegistries.BLOCKS.getKey(value),value))));
        ForgeRegistries.FLUIDS.forEach(value->jobs.add(()->decorated.accept(new FluidRegistryCollector().collect(ForgeRegistries.FLUIDS.getKey(value),value))));
        metadata(ForgeRegistries.RECIPE_TYPES,RuntimeData.Kind.RECIPE_TYPE,jobs,output);metadata(ForgeRegistries.RECIPE_SERIALIZERS,RuntimeData.Kind.RECIPE_SERIALIZER,jobs,output);return jobs;
    }
    private <T> void metadata(final IForgeRegistry<T> registry,final RuntimeData.Kind kind,final List<Runnable> jobs,final Consumer<RuntimeData.Registry> output) {
        registry.forEach(value->jobs.add(()->{final var id=registry.getKey(value);output.accept(new RuntimeData.Registry(kind,id.toString(),id.toString(),value.getClass().getName(),Map.of("namespace",id.getNamespace()),Map.of()));}));
    }
}
