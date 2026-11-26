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
    public InventoryPreviewProvider(){worker.scheduleWithFixedDelay(this::poll,2,2,TimeUnit.SECONDS);}
    public ReadOnlyLongProperty revisionProperty(){return revision.getReadOnlyProperty();}
    public void client(final ItemPreviewClient value){
        if(observed!=null)observed.removeListener(connectionListener);
        client=value;observed=value instanceof dev.recipeeditor.companionclient.CompanionConnectionService service?service:null;
        if(observed!=null)observed.addListener(connectionListener);connectionChanged();
    }
    private void connectionChanged(){epoch.incrementAndGet();liveContext=runtimeContext();refreshed=System.nanoTime();invalidate();}
    private String runtimeContext(){final var active=client;return active!=null&&active.itemPreviewsAvailable()?active.previewContext():"offline";}
    public String clientStatus(){final var active=client;return active==null?"CLIENT_DISCONNECTED: connect the Minecraft client in Runtime":active.previewUnavailableReason();}
    /** Native-only diagnostic request, using the same connection/limits as catalog previews. No static success substitute. */
    public CompletableFuture<ItemPreviewCache.Image> requestClientPreview(final String id,final int size){
        final var request=new ItemPreviewRequest(id,size);final var active=client;final long generation=epoch.get();
        if(active==null||!active.itemPreviewsAvailable())return CompletableFuture.completedFuture(missing(clientStatus()));
        final var cached=cache.get(nativeCacheKey(request,active.previewContext()));
        if(cached.isPresent()){final var image=cached.get();return CompletableFuture.completedFuture(new ItemPreviewCache.Image(image.png(),image.source(),"CACHE_HIT: previously transferred native PNG; "+image.diagnostic()));}
        return active.itemPreviews(List.of(request)).thenApply(rows->{
            if(active!=client||generation!=epoch.get())return missing("CLIENT_CONTEXT_CHANGED");
            if(rows.size()!=1||!rows.getFirst().request().equals(request))return missing("PREVIEW_RESPONSE_IDENTITY_MISMATCH");
            final var response=rows.getFirst();if(!response.status().equals("READY"))return missing("PREVIEW_FAILED: "+response.diagnostic());
            final var image=new ItemPreviewCache.Image(response.png(),response.source(),"PREVIEW_RENDERED -> PREVIEW_TRANSFERRED; "+response.diagnostic());cache.put(nativeCacheKey(request,response.context()),image);return image;
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
    private void process(final List<Pending> batch){
        final var runtime=new ArrayList<Pending>();final var requests=new ArrayList<ItemPreviewRequest>();final var active=client;
        for(final var item:batch){
            if(item.consumers.get()==0){item.future.cancel(false);continue;}
            if(item.key.epoch()!=epoch.get()){item.future.complete(missing("CLIENT_CONTEXT_CHANGED"));continue;}
            try{
                final var context=context(item.key.registry());if(context.dirty){item.future.complete(missing("Resource files changed; re-index before showing cached icons"));continue;}
                if(item.key.kind()==ResourceKind.FLUID){item.future.complete(texture(item.key));continue;}
                final String token=active!=null&&active.itemPreviewsAvailable()?active.previewContext():"offline";
                final var cached=cache.get(cacheKey(item.key,context,token));if(cached.isPresent()){item.future.complete(cached.get());continue;}
                if(active!=null&&active.itemPreviewsAvailable()){final var request=new ItemPreviewRequest(context.item(item.key).toString(),item.key.size(),item.key.variant(),item.key.nbt());runtime.add(item);requests.add(request);}
                else completeStatic(item,context,clientStatus());
            }catch(Exception failure){item.future.complete(missing(message(failure)));}
        }
        if(runtime.isEmpty())return;
        try{
            final var responses=active.itemPreviews(requests).get(7,TimeUnit.SECONDS);
            for(int i=0;i<runtime.size();i++){
                final var item=runtime.get(i);final var response=responses.get(i);if(!response.request().equals(requests.get(i)))throw new IllegalArgumentException("Runtime preview identity mismatch");final var context=context(item.key.registry());
                if(active!=client||item.key.epoch()!=epoch.get()||context.dirty){item.future.complete(missing("Preview context changed; retry after synchronization"));continue;}
                if(response.status().equals("READY")){final var result=new ItemPreviewCache.Image(response.png(),response.source(),response.diagnostic());cache.put(cacheKey(item.key,context,response.context()),result);item.future.complete(result);}
                else completeStatic(item,context,response.diagnostic());
            }
        }catch(Exception unavailable){for(final var item:runtime)if(!item.future.isDone())try{completeStatic(item,context(item.key.registry()),"Client preview failed: "+message(unavailable));}catch(Exception failure){item.future.complete(missing(message(failure)));}}
    }
    private void completeStatic(final Pending pending,final Context context,final String clientDiagnostic){
        try{renderStatic(pending,context,clientDiagnostic);}catch(Exception failure){pending.future.complete(missing(clientDiagnostic+"; static fallback: "+message(failure)));}
    }
    private void renderStatic(final Pending pending,final Context context,final String clientDiagnostic)throws Exception{
        if(pending.consumers.get()==0){pending.future.cancel(false);return;}
        if(!pending.key.nbt().isEmpty()){pending.future.complete(missing("NBT variant needs Minecraft Client; "+clientDiagnostic));return;}
        final var itemId=context.item(pending.key);final InventoryItemModel model;
        try{model=context.model(itemId);}catch(Exception unavailable){pending.future.complete(missing(clientDiagnostic+"; static fallback: "+message(unavailable)));return;}
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
        if(runtimeChanged||filesChanged){cache.clear();frames.clear();notifyViews();}
    }catch(Exception ignored){cache.clear();}}
    private void notifyViews(){try{Platform.runLater(()->revision.set(revision.get()+1));}catch(IllegalStateException notStarted){/* No mounted previews before FX startup. */}}
    private ItemPreviewCache.Image missing(final String message){return new ItemPreviewCache.Image(new byte[0],"PLACEHOLDER",message);}
    private String message(final Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();return cause.getClass().getSimpleName()+": "+Objects.toString(cause.getMessage(),"Preview unavailable");}
    private static String digest(final String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    @Override public void close(){synchronized(pending){closed=true;pending.values().forEach(p->p.future.cancel(false));pending.clear();}if(observed!=null)observed.removeListener(connectionListener);worker.shutdownNow();frames.close();cache.clear();}
}
