package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionprotocol.ProtocolException;
import dev.recipeeditor.companionprotocol.RuntimeData.*;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** All socket I/O runs on one worker. Cancellation closes the socket to release blocked reads. */
public final class CompanionConnectionService implements AutoCloseable,ItemPreviewClient {
    private static final class PreviewUnavailable extends IOException {private PreviewUnavailable(final String message){super(message);}}
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("companion-client",0).factory());
    private final List<Consumer<CompanionConnectionStatus>> listeners=new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("companion-heartbeat",0).factory());
    private final java.util.concurrent.atomic.AtomicBoolean heartbeatPending=new java.util.concurrent.atomic.AtomicBoolean();
    private final ProtocolCodec codec=new ProtocolCodec();
    private volatile Socket socket;
    private volatile CompanionConnectionStatus status=CompanionConnectionStatus.DISCONNECTED;
    private volatile boolean cancelled;
    private SessionCredentials credentials;
    private String companionVersion="";
    private boolean materialsSupported;
    private volatile boolean previewsSupported;
    private volatile String previewContext="";
    private final Semaphore previewSlots=new Semaphore(8);
    private final Set<CompletableFuture<List<ItemPreviewResponse>>> previewFutures=ConcurrentHashMap.newKeySet();
    private final long syncKeepaliveNanos;
    private final Set<String> pendingKeepalives=new HashSet<>();
    public CompanionConnectionService(){this(java.time.Duration.ofSeconds(30));}
    CompanionConnectionService(final java.time.Duration keepaliveInterval){syncKeepaliveNanos=keepaliveInterval.toNanos();if(syncKeepaliveNanos<=0)throw new IllegalArgumentException("Positive keepalive interval required");heartbeat.scheduleWithFixedDelay(()->{if(status==CompanionConnectionStatus.CONNECTED&&heartbeatPending.compareAndSet(false,true))try{worker.submit(()->{try{if(status==CompanionConnectionStatus.CONNECTED)request(MessageType.PING,new JsonObject(),MessageType.PONG);}catch(Exception lost){closeSocket();state(cancelled?CompanionConnectionStatus.DISCONNECTED:CompanionConnectionStatus.ERROR);}finally{heartbeatPending.set(false);}});}catch(RejectedExecutionException ended){heartbeatPending.set(false);}},5,5,TimeUnit.SECONDS);}
    public CompanionConnectionStatus status(){return status;}
    public String companionVersion(){return companionVersion;}
    public void addListener(final Consumer<CompanionConnectionStatus> listener){listeners.add(listener);}
    private void state(final CompanionConnectionStatus value){status=value;listeners.forEach(listener->listener.accept(value));}
    public CompletableFuture<Void> connect(final Path sessionFile){return CompletableFuture.runAsync(()->{try{open(SessionCredentials.load(sessionFile));}catch(Exception failure){fail(failure);}},worker);}
    public CompletableFuture<Void> connect(final SessionCredentials session){return CompletableFuture.runAsync(()->{try{open(session);}catch(Exception failure){fail(failure);}},worker);}
    private void open(final SessionCredentials session)throws IOException {
        closeSocket();pendingKeepalives.clear();cancelled=false;state(CompanionConnectionStatus.CONNECTING);credentials=Objects.requireNonNull(session);
        final var connected=new Socket();socket=connected;connected.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),session.port()),3000);connected.setSoTimeout(5000);
        final String nonce=SessionCredentials.nonce();final var hello=new JsonObject();hello.addProperty("clientNonce",nonce);final var challenge=request(MessageType.HANDSHAKE,hello,MessageType.HANDSHAKE_RESPONSE);
        final String server=ProtocolCodec.required(challenge,"serverNonce");
        if(!session.sessionId().equals(ProtocolCodec.required(challenge,"sessionId"))||!session.matches(session.proof("server",nonce,server),ProtocolCodec.required(challenge,"proof")))throw new ProtocolException("Companion server authentication failed");
        final var auth=new JsonObject();auth.addProperty("proof",session.proof("client",nonce,server));final var response=request(MessageType.HANDSHAKE,auth,MessageType.HANDSHAKE_RESPONSE);
        if(!response.has("authenticated")||!response.get("authenticated").getAsBoolean())throw new ProtocolException("Authentication rejected");
        previewsSupported=response.has("capabilities")&&response.getAsJsonArray("capabilities").contains(new com.google.gson.JsonPrimitive("item-previews-v1"));previewContext=response.has("previewContext")?response.get("previewContext").getAsString():"";
        companionVersion=ProtocolCodec.required(response,"companionVersion");materialsSupported=response.has("capabilities")&&response.getAsJsonArray("capabilities").contains(new com.google.gson.JsonPrimitive("materials-v1"));connected.setSoTimeout(15000);connected();
    }
    public boolean itemPreviewsAvailable(){return status==CompanionConnectionStatus.CONNECTED&&previewsSupported;}
    public String previewContext(){return previewContext;}
    public CompletableFuture<List<ItemPreviewResponse>> itemPreviews(final List<ItemPreviewRequest> items){
        final var requests=List.copyOf(items);if(requests.isEmpty()||requests.size()>4)return CompletableFuture.failedFuture(new IllegalArgumentException("Batch limit: 1..4"));
        if(!itemPreviewsAvailable())return CompletableFuture.failedFuture(new IllegalStateException("Client inventory preview unavailable"));
        if(!previewSlots.tryAcquire())return CompletableFuture.failedFuture(new RejectedExecutionException("Preview queue full"));
        final var result=new CompletableFuture<List<ItemPreviewResponse>>();previewFutures.add(result);result.whenComplete((value,error)->previewFutures.remove(result));
        try{worker.execute(()->{
            try{
                if(result.isDone())return;if(!itemPreviewsAvailable())throw new IOException("Runtime disconnected or synchronizing");
                final var payload=new JsonObject();payload.add("items",ProtocolCodec.GSON.toJsonTree(requests));socket.setSoTimeout(6000);
                final var response=request(MessageType.ITEM_PREVIEW_REQUEST,payload,MessageType.ITEM_PREVIEW_RESPONSE);
                if(!response.keySet().equals(Set.of("items"))||!response.get("items").isJsonArray()||response.getAsJsonArray("items").size()!=requests.size())throw new ProtocolException("Invalid preview batch response");
                final var rows=new ArrayList<ItemPreviewResponse>();int index=0;
                for(final var item:response.getAsJsonArray("items")){final var row=ProtocolCodec.GSON.fromJson(item,ItemPreviewResponse.class);if(!row.request().equals(requests.get(index++)))throw new ProtocolException("Preview identity mismatch");rows.add(row);if(row.status().equals("READY"))previewContext=row.context();}
                result.complete(List.copyOf(rows));
            }catch(PreviewUnavailable unavailable){result.completeExceptionally(unavailable);}
            catch(Exception failure){closeSocket();state(cancelled?CompanionConnectionStatus.DISCONNECTED:CompanionConnectionStatus.ERROR);result.completeExceptionally(failure);}
            finally{previewSlots.release();final var connected=socket;if(connected!=null)try{connected.setSoTimeout(15000);}catch(IOException ignored){}}
        });}catch(RejectedExecutionException ended){previewSlots.release();result.completeExceptionally(ended);}return result.orTimeout(8,TimeUnit.SECONDS);
    }
    public CompletableFuture<Snapshot> synchronize(final Consumer<String> progress) {
        return CompletableFuture.supplyAsync(()->{
            try {
                if(status!=CompanionConnectionStatus.CONNECTED)throw new IOException("Connect before synchronization");cancelled=false;state(CompanionConnectionStatus.SYNCHRONIZING);
                final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(300);
                final var registries=dataset(MessageType.GET_REGISTRIES,MessageType.REGISTRY_DATA,Registry.class,progress,deadline,256L*1024*1024);
                final var tags=dataset(MessageType.GET_TAGS,MessageType.TAG_DATA,Tag.class,progress,deadline,256L*1024*1024-registries.completion().get("bytes").getAsLong());
                final var recipes=dataset(MessageType.GET_RECIPES,MessageType.RECIPE_DATA,Recipe.class,progress,deadline,256L*1024*1024-registries.completion().get("bytes").getAsLong()-tags.completion().get("bytes").getAsLong());
                final var mods=dataset(MessageType.GET_MOD_LIST,MessageType.MOD_LIST_DATA,Mod.class,progress,deadline,256L*1024*1024-registries.completion().get("bytes").getAsLong()-tags.completion().get("bytes").getAsLong()-recipes.completion().get("bytes").getAsLong());
                final var materials=materialsSupported?dataset(MessageType.GET_MATERIALS,MessageType.MATERIAL_DATA,Material.class,progress,deadline,256L*1024*1024-registries.completion().get("bytes").getAsLong()-tags.completion().get("bytes").getAsLong()-recipes.completion().get("bytes").getAsLong()-mods.completion().get("bytes").getAsLong()):null;
                final var base=registries.completion();for(final var other:List.of(tags.completion(),recipes.completion(),mods.completion()))if(!other.get("snapshotId").equals(base.get("snapshotId"))||!other.get("environment").equals(base.get("environment")))throw new ProtocolException("Environment changed during synchronization");
                final var environment=ProtocolCodec.GSON.fromJson(base.get("environment"),Environment.class);
                if(materials!=null&&(!materials.completion().get("snapshotId").equals(base.get("snapshotId"))||!materials.completion().get("environment").equals(base.get("environment"))))throw new ProtocolException("Material environment changed during synchronization");
                if(!environment.sessionId().equals(credentials.sessionId())||!environment.minecraftVersion().equals("1.20.1")||!environment.loader().equals("FORGE")||!environment.loaderVersion().startsWith("47."))throw new ProtocolException("Unsupported or mismatched runtime environment");
                final var ping=request(MessageType.PING,new JsonObject(),MessageType.PONG);if(!ping.get("ready").getAsBoolean()||ping.get("generation").getAsLong()!=environment.generation())throw new ProtocolException("Runtime reloaded during synchronization");
                final var diagnostics=new ArrayList<String>();base.getAsJsonArray("diagnostics").forEach(d->diagnostics.add(d.getAsString()));
                if(!materialsSupported)diagnostics.add("Companion does not support materials-v1; material catalog requires an updated Companion");
                final var result=new Snapshot(materialsSupported?2:1,ProtocolCodec.required(base,"snapshotId"),true,environment,registries.records(),tags.records(),recipes.records(),mods.records(),diagnostics,materials==null?List.of():materials.records());
                connected();return result;
            }catch(Exception failure){fail(failure);throw new AssertionError("unreachable");}
            finally{if(status==CompanionConnectionStatus.SYNCHRONIZING){closeSocket();state(cancelled?CompanionConnectionStatus.DISCONNECTED:CompanionConnectionStatus.ERROR);}}
        },worker);
    }
    private <T> RecordTransfer.Received<T> dataset(final MessageType request,final MessageType response,final Class<T> type,final Consumer<String> progress,final long deadline,final long byteBudget)throws IOException {
        if(cancelled)throw new InterruptedIOException("Synchronization cancelled");progress.accept("Receiving "+request.name());final String id=UUID.randomUUID().toString();codec.write(socket.getOutputStream(),new ProtocolMessage(request,id,new JsonObject()));
        final long[] lastKeepalive={System.nanoTime()};
        return new RecordTransfer(byteBudget).receive(()->{
            if(cancelled||System.nanoTime()>deadline)throw new SocketTimeoutException("Synchronization deadline exceeded");
            if(System.nanoTime()-lastKeepalive[0]>=syncKeepaliveNanos&&pendingKeepalives.isEmpty()) {
                final String ping=UUID.randomUUID().toString();pendingKeepalives.add(ping);lastKeepalive[0]=System.nanoTime();codec.write(socket.getOutputStream(),new ProtocolMessage(MessageType.PING,ping,new JsonObject()));
            }
            return readMessage();
        },id,response,type,p->progress.accept(p.has("stage")?p.get("stage").getAsString():"Synchronizing"));
    }
    private JsonObject request(final MessageType type,final JsonObject payload,final MessageType expected)throws IOException {
        final String id=UUID.randomUUID().toString();codec.write(socket.getOutputStream(),new ProtocolMessage(type,id,payload));final var response=readMessage();if(type==MessageType.ITEM_PREVIEW_REQUEST&&response.requestId().equals(id)&&(response.messageType()==MessageType.ITEM_PREVIEW_ERROR||response.messageType()==MessageType.ERROR))throw new PreviewUnavailable(response.payload().has("code")?response.payload().get("code").getAsString():"Preview temporarily unavailable");if(type==MessageType.PING&&response.payload().has("previewContext"))previewContext=response.payload().get("previewContext").getAsString();if(!response.requestId().equals(id)||response.messageType()!=expected)throw new ProtocolException("Unexpected response to "+type);return response.payload();
    }
    private ProtocolMessage readMessage() throws IOException {
        while(true) {
            final var message=codec.read(socket.getInputStream());
            if(!pendingKeepalives.remove(message.requestId()))return message;
            if(message.messageType()!=MessageType.PONG||!message.payload().has("ready")||!message.payload().get("ready").getAsBoolean())throw new ProtocolException("Runtime stopped during synchronization keepalive");
        }
    }
    private synchronized void connected(){if(cancelled||socket==null||socket.isClosed())throw new CancellationException("Connection cancelled");state(CompanionConnectionStatus.CONNECTED);}
    private void fail(final Exception failure){closeSocket();state(cancelled?CompanionConnectionStatus.DISCONNECTED:CompanionConnectionStatus.ERROR);throw new CompletionException(cancelled?new CancellationException("Synchronization cancelled"):failure);}
    public synchronized void disconnect(){cancelled=true;closeSocket();previewFutures.forEach(f->f.completeExceptionally(new CancellationException("Preview connection closed")));previewFutures.clear();state(CompanionConnectionStatus.DISCONNECTED);}
    public void cancelSynchronization(){disconnect();}
    private void closeSocket(){final var current=socket;socket=null;if(current!=null)try{current.close();}catch(IOException ignored){}}
    @Override public void close(){disconnect();heartbeat.shutdownNow();worker.shutdownNow();}
}
