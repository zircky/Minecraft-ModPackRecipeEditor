package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import java.util.*;
import java.util.function.Consumer;
public final class RuntimeTagCollector {
    public List<Runnable> jobs(final MinecraftServer server,final Consumer<RuntimeData.Tag> output) {
        final var jobs=new ArrayList<Runnable>();add(server.registryAccess().registryOrThrow(Registries.ITEM),RuntimeData.Kind.ITEM,jobs,output);add(server.registryAccess().registryOrThrow(Registries.BLOCK),RuntimeData.Kind.BLOCK,jobs,output);add(server.registryAccess().registryOrThrow(Registries.FLUID),RuntimeData.Kind.FLUID,jobs,output);return jobs;
    }
    private <T> void add(final Registry<T> registry,final RuntimeData.Kind kind,final List<Runnable> jobs,final Consumer<RuntimeData.Tag> output) {
        registry.getTags().forEach(pair->jobs.add(()->output.accept(new RuntimeData.Tag(kind,pair.getFirst().location().toString(),pair.getSecond().stream().map(holder->Objects.requireNonNull(registry.getKey(holder.value())).toString()).sorted().toList()))));
    }
}
