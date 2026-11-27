package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.companionclient.ItemPreviewClient;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.beans.property.*;

/** Application-scoped inventory pipeline shared by every item surface; all I/O and parsing is off FX. */
public final class InventoryPreviewProvider implements AutoCloseable {
    private static final class Shared {private static final InventoryPreviewProvider INSTANCE=new InventoryPreviewProvider();}
    public static InventoryPreviewProvider shared(){return Shared.INSTANCE;}
    private record PendingKey(RegistryIndex registry,Path root,ResourceKind kind,ResourceLocation id,int size,String variant,String nbt,long epoch){}
    private static final class Pending {
        private final PendingKey key;private final CompletableFuture<ItemPreviewCache.Image> future=new CompletableFuture<>();private final AtomicInteger consumers=new AtomicInteger();private boolean running;
        private Pending(final PendingKey key){this.key=key;}
    }
    private static final class Context {
        private final RegistryIndex registry;private final InventoryItemModelResolver models;private final String id=UUID.randomUUID().toString();
        private final List<Path> files;private final String original;private long checked;private boolean dirty;
        private BlockItemRelationships relationships;
        private final Map<ResourceLocation,GregTechMaterialPreview.Definition> dynamic=new HashMap<>();
        private final Map<ResourceLocation,String> materialKeys=new HashMap<>();
        private final LinkedHashMap<ResourceLocation,InventoryItemModel> parsed=new LinkedHashMap<>(32,0.75f,true);
        private Context(final RegistryIndex index){registry=index;index.materials().forEach(m->{final String identity=digest(m.id()+m.metadata().toString());m.forms().forEach(f->materialKeys.put(f.resourceId(),identity));});models=new InventoryItemModelResolver(index.assetCatalog());final var paths=new HashSet<Path>();index.assetCatalog().records().forEach(a->{final var source=a.source();paths.add(Files.isDirectory(source.container())?source.container().resolve(source.internalPath()):source.container());});index.entries().forEach(e->e.sources().stream().filter(s->s.type()==SourceType.RUNTIME).forEach(s->paths.add(s.container())));files=paths.stream().sorted().toList();original=signature();checked=System.nanoTime();}
        private String signature(){final var text=new StringBuilder();for(final var path:files)try{text.append(path).append(':').append(Files.size(path)).append(':').append(Files.getLastModifiedTime(path)).append(';');}catch(IOException missing){text.append(path).append(":MISSING;");}return digest(text.toString());}
        private boolean changed(){if(dirty)return false;final long now=System.nanoTime();if(now-checked<TimeUnit.SECONDS.toNanos(2))return false;checked=now;if(!original.equals(signature())){dirty=true;return true;}return false;}
        private InventoryItemModel model(final ResourceLocation item){var result=parsed.get(item);if(result==null){try{result=models.resolve(item);}catch(BlockModelException unavailable){if(!materialKeys.containsKey(item))throw unavailable;final var generated=new GregTechMaterialPreview(registry).resolve(item);dynamic.put(item,generated);result=generated.model();}parsed.put(item,result);while(parsed.size()>128){final var removed=parsed.pollFirstEntry();dynamic.remove(removed.getKey());}}return result;}
        private ResourceLocation item(final PendingKey key){if(key.kind()==ResourceKind.ITEM)return key.id();if(relationships==null)relationships=new BlockItemRelationships(registry);final var items=relationships.items(key.id());if(items.size()!=1)throw new IllegalArgumentException("Technical Block has no unambiguous registered BlockItem; use Items catalog");return items.getFirst();}
    }
    private final ItemPreviewCache cache=new ItemPreviewCache(256,8*1024*1024);
    private final PersistentItemPreviews disk;
    private record SnapshotScope(RegistryIndex registry,String key,String sessionId,Path instance) {}
    private volatile SnapshotScope snapshot;
    private final TextureResourceService frames=new TextureResourceService();
    private final StaticInventoryRenderer renderer=new StaticInventoryRenderer();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("inventory-preview-worker").factory());
    private final LinkedHashMap<PendingKey,Pending> pending=new LinkedHashMap<>();
    private final LinkedHashMap<RegistryIndex,Context> contexts=new LinkedHashMap<>(4,0.75f,true);
    private final ReadOnlyLongWrapper revision=new ReadOnlyLongWrapper();
    private volatile ItemPreviewClient client;
    private final java.util.concurrent.atomic.AtomicLong epoch=new java.util.concurrent.atomic.AtomicLong();
    private dev.recipeeditor.companionclient.CompanionConnectionService observed;
    private final java.util.function.Consumer<dev.recipeeditor.companionclient.CompanionConnectionStatus> connectionListener=status->connectionChanged();
    private volatile String liveContext="offline";
    private volatile long refreshed=System.nanoTime();
    private boolean pumping,closed;
    public InventoryPreviewProvider(){this(dev.recipeeditor.core.ApplicationDirectories.current().cache().resolve("item-previews-v1"));}
    InventoryPreviewProvider(final Path directory){disk=new PersistentItemPreviews(directory);worker.scheduleWithFixedDelay(this::poll,2,2,TimeUnit.SECONDS);}
    public void bindSnapshot(final RegistryIndex registry,final RuntimeData.Snapshot data,final String fingerprint){
        snapshot=new SnapshotScope(registry,String.join("|",data.environment().minecraftVersion(),fingerprint,data.snapshotId(),"native-renderer-1"),
                data.environment().sessionId(),Path.of(data.environment().sourceInstance()).toAbsolutePath().normalize());
        notifyViews();
    }
    public void clearSnapshot(){snapshot=null;}
    public boolean canCollect(){
        final var scope=snapshot;final var active=observed;
        return scope!=null&&active!=null&&(active.matchesPreviewSession(scope.sessionId())||active.matchesPreviewInstance(scope.instance()));
    }
    private String diskKey(final ItemPreviewRequest request) {
        return String.join("|",request.resourceId(),request.variant(),digest(request.nbt()),Integer.toString(request.size()));
    }
    private ItemPreviewRequest nativeRequest(final PendingKey key,final Context context){
        return new ItemPreviewRequest(context.item(key).toString(),key.size(),key.variant(),key.nbt());
    }
    public ReadOnlyLongProperty revisionProperty(){return revision.getReadOnlyProperty();}
    public void client(final ItemPreviewClient value){
        if(observed!=null)observed.removeListener(connectionListener);
        client=value;observed=value instanceof dev.recipeeditor.companionclient.CompanionConnectionService service?service:null;
        if(observed!=null)observed.addListener(connectionListener);connectionChanged();
    }
    private void connectionChanged(){epoch.incrementAndGet();liveContext=runtimeContext();refreshed=System.nanoTime();cache.clearSession();notifyViews();}
    private String runtimeContext(){final var active=client;return active!=null&&active.itemPreviewsAvailable()?active.previewContext():"offline";}
    public String clientStatus(){final var active=client;return active==null?"CLIENT_DISCONNECTED: connect the Minecraft client in Runtime":active.previewUnavailableReason();}
    /** Native-only diagnostic request, using the same connection/limits as catalog previews. No static success substitute. */
    public CompletableFuture<ItemPreviewCache.Image> requestClientPreview(final String id,final int size){
        final var request=new ItemPreviewRequest(id,size);final var active=client;final long generation=epoch.get();
        if(active==null||!active.itemPreviewsAvailable())return CompletableFuture.completedFuture(missing(clientStatus()));
        final var cached=cache.get(nativeCacheKey(request,active.previewContext()));
        if(cached.isPresent()){final var image=cached.get();return CompletableFuture.completedFuture(new ItemPreviewCache.Image(image.png(),image.source(),"CACHE_HIT: previously transferred native PNG; "+image.diagnostic()));}
        return active.itemPreviews(List.of(request)).thenApplyAsync(rows->{
            if(active!=client||generation!=epoch.get())return missing("CLIENT_CONTEXT_CHANGED");
            if(rows.size()!=1||!rows.getFirst().request().equals(request))return missing("PREVIEW_RESPONSE_IDENTITY_MISMATCH");
            final var response=rows.getFirst();if(!response.status().equals("READY"))return missing("PREVIEW_FAILED: "+response.diagnostic());
            final var scope=snapshot;
            String diagnostic="PREVIEW_RENDERED -> PREVIEW_TRANSFERRED; "+response.diagnostic();
            if(scope!=null&&canCollect()&&!scope.registry().find(ResourceLocation.parse(id)).isEmpty())
                try{save(scope,response);}catch(IOException failure){diagnostic+="; disk cache: "+failure.getMessage();}
            final var image=new ItemPreviewCache.Image(response.png(),response.source(),diagnostic);cache.put(nativeCacheKey(request,response.context()),image);return image;
        }).exceptionally(error->missing("PREVIEW_FAILED: "+message(error)));
    }
    public void invalidate(){cache.clear();notifyViews();}
    public CompletableFuture<ItemPreviewCache.Image> preview(final RegistryEntry entry,final RegistryIndex index,final Path root,final int requestedSize,final String nbt){
        final int size=requestedSize<=64?64:128;final var key=new PendingKey(index,root.toAbsolutePath().normalize(),entry.kind(),entry.id(),size,nbt.isEmpty()?"default":"nbt",nbt,epoch.get());
        synchronized(pending){
            if(closed)return CompletableFuture.completedFuture(missing("Preview service closed"));
            Pending request=pending.get(key);if(request!=null&&request.future.isDone()){pending.remove(key,request);request=null;}if(request==null){if(pending.size()>=128)return CompletableFuture.completedFuture(missing("Preview backpressure: queue full; select item to retry"));request=new Pending(key);pending.put(key,request);}
            final var chosen=request;chosen.consumers.incrementAndGet();final var consumer=new CompletableFuture<ItemPreviewCache.Image>();
            chosen.future.whenComplete((image,error)->{if(error==null)consumer.complete(image);else consumer.completeExceptionally(error);});consumer.whenComplete((image,error)->chosen.consumers.decrementAndGet());
            if(!pumping){pumping=true;worker.execute(this::pump);}return consumer.orTimeout(12,TimeUnit.SECONDS);
        }
    }
    public CompletableFuture<ItemPreviewCache.Image> preview(final RegistryEntry entry,final RegistryIndex index,final Path root,final int size){return preview(entry,index,root,size,"");}
    private void pump(){
        try{while(true){final List<Pending> batch;
            synchronized(pending){batch=pending.values().stream().filter(p->!p.running).limit(4).toList();batch.forEach(p->p.running=true);if(batch.isEmpty()){pumping=false;return;}}
            process(batch);
            synchronized(pending){batch.forEach(p->pending.remove(p.key,p));}
        }}catch(Exception failure){synchronized(pending){pending.values().forEach(p->p.future.complete(missing("Preview worker failed: "+failure.getClass().getSimpleName())));pending.clear();pumping=false;}}
    }
    private Context context(final RegistryIndex index){Context result=contexts.get(index);if(result==null){result=new Context(index);contexts.put(index,result);while(contexts.size()>2)contexts.pollFirstEntry();frames.clear();}if(result.changed()){cache.clear();frames.clear();notifyViews();}return result;}
    private ItemPreviewCache.Key nativeCacheKey(final ItemPreviewRequest request,final String context){return new ItemPreviewCache.Key(request.resourceId(),ResourceKind.ITEM,request.variant(),digest(request.nbt()),"1.20.1","native-client-session:"+context,request.size(),"inventory-renderer-1");}
    private ItemPreviewCache.Key cacheKey(final PendingKey key,final Context context,final String runtime){return new ItemPreviewCache.Key(key.id().toString(),key.kind(),key.variant(),digest(key.nbt()),"1.20.1",context.id+":"+context.original+":"+context.materialKeys.getOrDefault(key.id(),"")+":"+runtime,key.size(),"inventory-renderer-1");}
    private Optional<ItemPreviewCache.Key> capturedKey(final PendingKey key,final Context context) {
        final var sources=context.registry.find(key.id()).stream().filter(entry->entry.kind()==key.kind())
                .flatMap(entry->entry.sources().stream()).filter(source->source.type()==SourceType.RUNTIME
                        && source.internalPath().startsWith("snapshot/")).distinct().toList();
        if(sources.size()!=1)return Optional.empty();
        final var source=sources.getFirst();
        return Optional.of(new ItemPreviewCache.Key(key.id().toString(),key.kind(),key.variant(),digest(key.nbt()),"1.20.1",
                "snapshot-preview:"+source.container()+":"+source.internalPath()+":"+context.original,key.size(),"inventory-renderer-1"));
    }
    private void process(final List<Pending> batch){
        final var runtime=new ArrayList<Pending>();final var requests=new ArrayList<ItemPreviewRequest>();final var active=client;
        for(final var item:batch){
            if(item.consumers.get()==0){item.future.cancel(false);continue;}
            if(item.key.epoch()!=epoch.get()){item.future.complete(missing("CLIENT_CONTEXT_CHANGED"));continue;}
            try{
                final var context=context(item.key.registry());if(context.dirty){item.future.complete(missing("Resource files changed; re-index before showing cached icons"));continue;}
                if(item.key.kind()==ResourceKind.FLUID){item.future.complete(texture(item.key));continue;}
                final var scope=snapshot;
                final boolean bound=scope!=null&&scope.registry()==item.key.registry();
                final boolean live=active!=null&&active.itemPreviewsAvailable()&&(!bound||canCollect());
                final String token=live?active.previewContext():"offline";
                String cacheState="";
                if(bound){
                    final var request=nativeRequest(item.key,context);
                    var stored=disk.read(scope.key(),diskKey(request),request.size(),live?token:null);
                    int storedSize=request.size();
                    if(!live&&stored.status()!=PersistentItemPreviews.Status.READY&&request.size()==128){
                        final var smaller=new ItemPreviewRequest(request.resourceId(),64,request.variant(),request.nbt());
                        final var fallback=disk.read(scope.key(),diskKey(smaller),64,null);
                        if(fallback.status()==PersistentItemPreviews.Status.READY){stored=fallback;storedSize=64;}
                    }
                    if(stored.status()==PersistentItemPreviews.Status.READY){
                        item.future.complete(new ItemPreviewCache.Image(stored.png(),"PERSISTENT_PREVIEW_CACHE","READY · native-renderer-1 · "
                                +storedSize+" px · "+stored.diagnostic()));
                        continue;
                    }
                    cacheState=" · "+stored.status()+" · "+stored.diagnostic();
                }
                if(token.equals("offline")) {
                    final var captured=capturedKey(item.key,context).flatMap(cache::get);
                    if(captured.isPresent()){item.future.complete(captured.get());continue;}
                }
                final var cached=cache.get(cacheKey(item.key,context,token));if(cached.isPresent()){item.future.complete(cached.get());continue;}
                if(live){
                    final var request=nativeRequest(item.key,context);
                    runtime.add(item);requests.add(request);
                }
                else completeStatic(item,context,clientStatus()+cacheState);
            }catch(Exception failure){item.future.complete(missing(message(failure)));}
        }
        if(runtime.isEmpty())return;
        try{
            final var responses=active.itemPreviews(requests).get(7,TimeUnit.SECONDS);
            for(int i=0;i<runtime.size();i++){
                final var item=runtime.get(i);final var response=responses.get(i);if(!response.request().equals(requests.get(i)))throw new IllegalArgumentException("Runtime preview identity mismatch");final var context=context(item.key.registry());
                if(active!=client||item.key.epoch()!=epoch.get()||context.dirty){item.future.complete(missing("Preview context changed; retry after synchronization"));continue;}
                if(response.status().equals("READY")){
                    final var result=new ItemPreviewCache.Image(response.png(),response.source(),response.diagnostic());
                    final var scope=snapshot;
                    if(scope!=null&&scope.registry()==item.key.registry()&&canCollect())
                        try{save(scope,response);}catch(IOException failure){
                            item.future.complete(new ItemPreviewCache.Image(response.png(),response.source(),"Disk cache write failed: "+failure.getMessage()));
                            continue;
                        }
                    cache.put(cacheKey(item.key,context,response.context()),result);
                    capturedKey(item.key,context).ifPresent(key->cache.put(key,result));item.future.complete(result);
                }
                else completeStatic(item,context,response.diagnostic());
            }
        }catch(Exception unavailable){for(final var item:runtime)if(!item.future.isDone())try{completeStatic(item,context(item.key.registry()),"Client preview failed: "+message(unavailable));}catch(Exception failure){item.future.complete(missing(message(failure)));}}
    }
    private void save(final SnapshotScope scope,final ItemPreviewResponse response)throws IOException{
        final var request=response.request();
        disk.write(scope.key(),diskKey(request),response.context(),request.size(),response.png(),response.diagnostic());
    }
    public enum CollectionMode { MISSING, FAILED, REBUILD }
    public record CollectionProgress(int registered,int cached,int missing,int failed,int corrupted,int stale,int processed) {}
    public CollectionProgress collect(final CollectionMode mode,final java.util.function.BooleanSupplier paused,
            final java.util.function.BooleanSupplier cancelled,final java.util.function.Consumer<CollectionProgress> progress)throws Exception{
        final var scope=snapshot;
        final var active=observed;
        if(scope==null||active==null||!canCollect())throw new IOException("Connect the Companion session that captured the merged snapshot");
        final String token=active.previewContext();
        final var items=scope.registry().entries(ResourceKind.ITEM).stream()
                .filter(entry->entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME)
                .map(entry->new ItemPreviewRequest(entry.id().toString(),64)).distinct().toList();
        final int[] counts=new int[PersistentItemPreviews.Status.values().length];
        final var queue=new ArrayList<ItemPreviewRequest>();
        for(final var item:items){
            if(cancelled.getAsBoolean())return progress(items.size(),counts,0);
            final var state=disk.read(scope.key(),diskKey(item),item.size(),token).status();
            counts[state.ordinal()]++;
            if(mode==CollectionMode.REBUILD|| (mode==CollectionMode.MISSING
                    ? state!=PersistentItemPreviews.Status.READY&&state!=PersistentItemPreviews.Status.FAILED
                    : state==PersistentItemPreviews.Status.FAILED||state==PersistentItemPreviews.Status.CORRUPTED))queue.add(item);
        }
        progress.accept(progress(items.size(),counts,items.size()-queue.size()));
        for(int offset=0;offset<queue.size();offset+=4){
            while(paused.getAsBoolean()&&!cancelled.getAsBoolean())Thread.sleep(150);
            if(cancelled.getAsBoolean())break;
            if(scope!=snapshot||observed!=active||!canCollect()||!token.equals(active.previewContext()))
                throw new IOException("Live preview context changed. Saved previews were retained.");
            final var batch=queue.subList(offset,Math.min(offset+4,queue.size()));
            List<ItemPreviewResponse> rows;
            try{rows=active.itemPreviews(batch).get(10,TimeUnit.SECONDS);}
            catch(ExecutionException first){Thread.sleep(350);if(cancelled.getAsBoolean())break;rows=active.itemPreviews(batch).get(10,TimeUnit.SECONDS);}
            if(rows.size()!=batch.size()||!canCollect()||scope!=snapshot||!token.equals(active.previewContext()))
                throw new IOException("Preview batch changed rendering context");
            for(int index=0;index<rows.size();index++){
                final var row=rows.get(index);final var item=batch.get(index);
                if(!row.request().equals(item)||(!row.context().isBlank()&&!row.context().equals(token)))
                    throw new IOException("Preview batch identity mismatch");
                final var before=disk.read(scope.key(),diskKey(item),item.size(),token).status();
                if(row.status().equals("READY"))save(scope,row);
                else if(before!=PersistentItemPreviews.Status.READY)
                    disk.write(scope.key(),diskKey(item),token,item.size(),new byte[0],row.diagnostic());
                final var after=disk.read(scope.key(),diskKey(item),item.size(),token).status();
                counts[before.ordinal()]--;counts[after.ordinal()]++;
            }
            progress.accept(progress(items.size(),counts,items.size()-queue.size()+offset+batch.size()));
            Thread.sleep(150);
        }
        notifyViews();
        return progress(items.size(),counts,cancelled.getAsBoolean()?0:items.size());
    }
    private CollectionProgress progress(final int size,final int[] counts,final int processed){
        return new CollectionProgress(size,counts[0],counts[1],counts[4],counts[3],counts[2],processed);
    }
    private void completeStatic(final Pending pending,final Context context,final String clientDiagnostic){
        try {
            renderStatic(pending,context,clientDiagnostic);
        } catch(Exception unavailable) {
            final String diagnostic=clientDiagnostic+"; static model: "+message(unavailable);
            try {
                final var entries=context.registry.find(context.item(pending.key)).stream().filter(entry->entry.kind()==ResourceKind.ITEM).toList();
                if(entries.size()==1) {
                    // The item model must establish generated layers. Runtime baked sprites can be a single face of a 3D model.
                    final var id=entries.getFirst().id();
                    final var generated=new ModelTextureResolver(context.registry.assetCatalog()).model(id.namespace()+":item/"+id.path());
                    final var actual=entries.getFirst().visual();
                    final boolean flatBaked=entries.getFirst().confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME
                            &&"LAYERS".equals(entries.getFirst().metadata().get("previewMode"))&&actual.status()==ResourceVisual.Status.READY;
                    if(!flatBaked&&(generated.status()!=ResourceVisual.Status.READY||!generated.explanation().equals("Generated item layers"))){
                        pending.future.complete(missing(diagnostic+"; no verified flat item model"));return;
                    }
                    final var visual=actual.status()==ResourceVisual.Status.READY&&!actual.layers().isEmpty()?actual:generated;
                    final var preview=frames.previewAsync(visual,pending.key.root()).get(5,TimeUnit.SECONDS);
                    if(preview.png().length>0&&pending.key.epoch()==epoch.get()) {
                        final var result=new ItemPreviewCache.Image(preview.png(),"STATIC_TEXTURE",diagnostic+"; "+preview.explanation());
                        cache.put(cacheKey(pending.key,context,"offline"),result);
                        pending.future.complete(result);
                        return;
                    }
                }
                pending.future.complete(missing(diagnostic));
            } catch(Exception fallback) {
                pending.future.complete(missing(diagnostic+"; texture fallback: "+message(fallback)));
            }
        }
    }
    private void renderStatic(final Pending pending,final Context context,final String clientDiagnostic)throws Exception{
        if(pending.consumers.get()==0){pending.future.cancel(false);return;}
        if(!pending.key.nbt().isEmpty()){pending.future.complete(missing("NBT variant needs Minecraft Client; "+clientDiagnostic));return;}
        final var itemId=context.item(pending.key);final var model=context.model(itemId);
        final var entries=context.registry.find(itemId).stream().filter(e->e.kind()==ResourceKind.ITEM).toList();
        final var loader=new InventoryModelTextures(frames);final var generated=context.dynamic.get(itemId);
        final var textures=generated==null?loader.load(model,entries.size()==1?entries.getFirst():null,pending.key.root()):loader.load(generated,entries.size()==1?entries.getFirst():null,pending.key.root());
        final String diagnostic=clientDiagnostic+"; actual item model; static GUI approximation"+(generated==null?"":"; GT8 material template: "+generated.identity())+(textures.tintUnknown()?"; TINT_UNKNOWN: runtime color required":"");
        byte[] png;
        try{
            final var pixels=renderer.render(model,textures.images(),textures.tinted(),pending.key.size()).get(5,TimeUnit.SECONDS);final var image=new BufferedImage(pixels.width(),pixels.height(),BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,pixels.width(),pixels.height(),pixels.argb(),0,pixels.width());final var output=new ByteArrayOutputStream();ImageIO.write(image,"PNG",output);png=output.toByteArray();
        }catch(Exception unavailable){
            if(model.kind()!=InventoryItemModel.Kind.GENERATED_2D||!model.gui().equals(GuiDisplayTransform.identity()))throw unavailable;
            final var result=new ItemPreviewCache.Image(textures.images().get(model.layers().layers().getFirst().source()),"STATIC_TEXTURE","Generated 2D item texture; 3D snapshot unavailable: "+message(unavailable));pending.future.complete(result);return;
        }
        final var result=new ItemPreviewCache.Image(png,model.kind()==InventoryItemModel.Kind.GEOMETRY?"STATIC_ITEM_MODEL_3D":"STATIC_ITEM_MODEL_2D",diagnostic);
        if(pending.key.epoch()!=epoch.get()){pending.future.complete(missing("CLIENT_CONTEXT_CHANGED"));return;}
        // Static fallback must never mask a later native response under the native cache key.
        cache.put(cacheKey(pending.key,context,"offline"),result);pending.future.complete(result);
    }
    private ItemPreviewCache.Image texture(final PendingKey key)throws Exception{final var entries=key.registry().find(key.id()).stream().filter(e->e.kind()==key.kind()).toList();if(entries.size()!=1)return missing("Ambiguous fluid resource");final var result=frames.previewAsync(entries.getFirst().visual(),key.root()).get(5,TimeUnit.SECONDS);return new ItemPreviewCache.Image(result.png(),"STATIC_TEXTURE",result.explanation());}
    private void poll(){if(closed)return;try{
        final var active=client;final String token=active!=null&&active.itemPreviewsAvailable()?active.previewContext():"offline";
        boolean runtimeChanged=!token.equals(liveContext);liveContext=token;
        if(active!=null&&active.itemPreviewsAvailable()&&System.nanoTime()-refreshed>TimeUnit.SECONDS.toNanos(10)){runtimeChanged=true;refreshed=System.nanoTime();}
        boolean filesChanged=false;
        for(final var context:contexts.values())if(context.changed())filesChanged=true;
        if(runtimeChanged)epoch.incrementAndGet();
        if(runtimeChanged||filesChanged){if(filesChanged)cache.clear();else cache.clearSession();frames.clear();notifyViews();}
    }catch(Exception ignored){cache.clear();}}
    private void notifyViews(){try{Platform.runLater(()->revision.set(revision.get()+1));}catch(IllegalStateException notStarted){/* No mounted previews before FX startup. */}}
    private ItemPreviewCache.Image missing(final String message){return new ItemPreviewCache.Image(new byte[0],"PLACEHOLDER",message);}
    private String message(final Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();return cause.getClass().getSimpleName()+": "+Objects.toString(cause.getMessage(),"Preview unavailable");}
    private static String digest(final String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    @Override public void close(){synchronized(pending){closed=true;pending.values().forEach(p->p.future.cancel(false));pending.clear();}if(observed!=null)observed.removeListener(connectionListener);worker.shutdownNow();frames.close();cache.clear();}
}
