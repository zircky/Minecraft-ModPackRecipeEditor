package dev.recipeeditor.forge;

import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.companionserver.SnapshotProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Game API stays on the server thread; at most 128 records / 4ms per tick. DTOs cross threads only. */
public final class ForgeSnapshotProvider implements SnapshotProvider {
    private volatile MinecraftServer server;
    private volatile long generation;
    private CollectionJob active;
    public void start(final MinecraftServer server){this.server=server;generation++;}
    public void invalidate(){generation++;if(active!=null){active.future.completeExceptionally(new IllegalStateException("Datapacks/world changed during collection"));active=null;}}
    public void stop(){invalidate();server=null;}
    @Override public boolean ready(){return server!=null;}
    @Override public long generation(){return generation;}
    @Override public CompletableFuture<RuntimeData.Snapshot> capture(final String sessionId,final BooleanSupplier cancelled,final Consumer<String> progress) {
        final var future=new CompletableFuture<RuntimeData.Snapshot>();final var current=server;
        if(current==null){future.completeExceptionally(new IllegalStateException("Open a local world / start a server first; remote client RecipeManager is not authoritative"));return future;}
        current.execute(()->{try{if(active!=null)throw new IllegalStateException("Collection busy");if(current!=server)throw new IllegalStateException("Server changed");active=new CollectionJob(current,generation,sessionId,cancelled,progress,future);}catch(RuntimeException|LinkageError failure){future.completeExceptionally(failure);}});return future;
    }
    public void tick() {
        final var job=active;if(job==null)return;
        try {if(job.future.isDone()||job.cancelled.getAsBoolean())throw new CancellationException("Collection cancelled");if(job.generation!=generation)throw new IllegalStateException("Runtime changed");final long deadline=System.nanoTime()+4_000_000;int processed=0;
            while(job.jobs.hasNext()&&processed++<128&&System.nanoTime()<deadline){job.jobs.next().run();job.completed++;}
            if(job.completed%1024<128)job.progress.accept("Collected "+job.completed+" / "+job.total+" records");
            if(!job.jobs.hasNext()){job.future.complete(job.snapshot());active=null;}
        }catch(RuntimeException|LinkageError failure){job.future.completeExceptionally(failure);active=null;}
    }
    private static final class CollectionJob {
        private final long generation;private final String session;private final BooleanSupplier cancelled;private final Consumer<String> progress;private final CompletableFuture<RuntimeData.Snapshot> future;
        private final List<RuntimeData.Registry> registries=new ArrayList<>();private final List<RuntimeData.Tag> tags=new ArrayList<>();private final List<RuntimeData.Recipe> recipes=new ArrayList<>();private final List<RuntimeData.Mod> mods;private final List<String> diagnostics=new ArrayList<>();private final Iterator<Runnable> jobs;private final int total;private int completed;
        private CollectionJob(final MinecraftServer server,final long generation,final String session,final BooleanSupplier cancelled,final Consumer<String> progress,final CompletableFuture<RuntimeData.Snapshot> future) {
            this.generation=generation;this.session=session;this.cancelled=cancelled;this.progress=progress;this.future=future;
            mods=ModList.get().getMods().stream().map(m->new RuntimeData.Mod(m.getModId(),m.getVersion().toString(),m.getDisplayName())).toList();
            final var tasks=new ArrayList<Runnable>();tasks.addAll(new RuntimeRegistryScanner().jobs(registries::add));tasks.addAll(new RuntimeTagCollector().jobs(server,tags::add));tasks.addAll(new RuntimeRecipeScanner().jobs(server,recipes::add,diagnostics));if(tasks.size()>500000)throw new IllegalStateException("Runtime collection record limit");total=tasks.size();jobs=tasks.iterator();
        }
        private RuntimeData.Snapshot snapshot() {return new RuntimeData.Snapshot(1,UUID.randomUUID().toString(),true,new RuntimeData.Environment(session,FMLPaths.GAMEDIR.get().toAbsolutePath().normalize().toString(),"1.20.1","FORGE",FMLLoader.versionInfo().forgeVersion(),dev.recipeeditor.companionprotocol.CompanionIdentity.version(),generation,java.time.Instant.now().toString()),registries,tags,recipes,mods,diagnostics);}
    }
}
