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
public final class CompanionConnectionService implements AutoCloseable {
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
    public CompanionConnectionService(){heartbeat.scheduleWithFixedDelay(()->{if(status==CompanionConnectionStatus.CONNECTED&&heartbeatPending.compareAndSet(false,true))try{worker.submit(()->{try{if(status==CompanionConnectionStatus.CONNECTED)request(MessageType.PING,new JsonObject(),MessageType.PONG);}catch(Exception lost){closeSocket();state(cancelled?CompanionConnectionStatus.DISCONNECTED:CompanionConnectionStatus.ERROR);}finally{heartbeatPending.set(false);}});}catch(RejectedExecutionException ended){heartbeatPending.set(false);}},5,5,TimeUnit.SECONDS);}
    public CompanionConnectionStatus status(){return status;}
    public String companionVersion(){return companionVersion;}
    public void addListener(final Consumer<CompanionConnectionStatus> listener){listeners.add(listener);}
    private void state(final CompanionConnectionStatus value){status=value;listeners.forEach(listener->listener.accept(value));}
    public CompletableFuture<Void> connect(final Path sessionFile){return CompletableFuture.runAsync(()->{try{open(SessionCredentials.load(sessionFile));}catch(Exception failure){fail(failure);}},worker);}
    public CompletableFuture<Void> connect(final SessionCredentials session){return CompletableFuture.runAsync(()->{try{open(session);}catch(Exception failure){fail(failure);}},worker);}
    private void open(final SessionCredentials session)throws IOException {
        closeSocket();cancelled=false;state(CompanionConnectionStatus.CONNECTING);credentials=Objects.requireNonNull(session);
        final var connected=new Socket();socket=connected;connected.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),session.port()),3000);connected.setSoTimeout(5000);
        final String nonce=SessionCredentials.nonce();final var hello=new JsonObject();hello.addProperty("clientNonce",nonce);final var challenge=request(MessageType.HANDSHAKE,hello,MessageType.HANDSHAKE_RESPONSE);
        final String server=ProtocolCodec.required(challenge,"serverNonce");
        if(!session.sessionId().equals(ProtocolCodec.required(challenge,"sessionId"))||!session.matches(session.proof("server",nonce,server),ProtocolCodec.required(challenge,"proof")))throw new ProtocolException("Companion server authentication failed");
        final var auth=new JsonObject();auth.addProperty("proof",session.proof("client",nonce,server));final var response=request(MessageType.HANDSHAKE,auth,MessageType.HANDSHAKE_RESPONSE);
        if(!response.has("authenticated")||!response.get("authenticated").getAsBoolean())throw new ProtocolException("Authentication rejected");
        companionVersion=ProtocolCodec.required(response,"companionVersion");connected.setSoTimeout(15000);connected();
    }
    public CompletableFuture<Snapshot> synchronize(final Consumer<String> progress) {
        return CompletableFuture.supplyAsync(()->{
            try {
                if(status!=CompanionConnectionStatus.CONNECTED)throw new IOException("Connect before synchronization");cancelled=false;state(CompanionConnectionStatus.SYNCHRONIZING);
                final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
                final var registries=dataset(MessageType.GET_REGISTRIES,MessageType.REGISTRY_DATA,Registry.class,progress,deadline,256L*1024*1024);
                final var tags=dataset(MessageType.GET_TAGS,MessageType.TAG_DATA,Tag.class,progress,deadline,256L*1024*1024-registries.completion().get("bytes").getAsLong());
                final var recipes=dataset(MessageType.GET_RECIPES,MessageType.RECIPE_DATA,Recipe.class,progress,deadline,256L*1024*1024-registries.completion().get("bytes").getAsLong()-tags.completion().get("bytes").getAsLong());
                final var mods=dataset(MessageType.GET_MOD_LIST,MessageType.MOD_LIST_DATA,Mod.class,progress,deadline,256L*1024*1024-registries.completion().get("bytes").getAsLong()-tags.completion().get("bytes").getAsLong()-recipes.completion().get("bytes").getAsLong());
                final var base=registries.completion();for(final var other:List.of(tags.completion(),recipes.completion(),mods.completion()))if(!other.get("snapshotId").equals(base.get("snapshotId"))||!other.get("environment").equals(base.get("environment")))throw new ProtocolException("Environment changed during synchronization");
                final var environment=ProtocolCodec.GSON.fromJson(base.get("environment"),Environment.class);
                if(!environment.sessionId().equals(credentials.sessionId())||!environment.minecraftVersion().equals("1.20.1")||!environment.loader().equals("FORGE")||!environment.loaderVersion().startsWith("47."))throw new ProtocolException("Unsupported or mismatched runtime environment");
                final var ping=request(MessageType.PING,new JsonObject(),MessageType.PONG);if(!ping.get("ready").getAsBoolean()||ping.get("generation").getAsLong()!=environment.generation())throw new ProtocolException("Runtime reloaded during synchronization");
                final var diagnostics=new ArrayList<String>();base.getAsJsonArray("diagnostics").forEach(d->diagnostics.add(d.getAsString()));
                final var result=new Snapshot(1,ProtocolCodec.required(base,"snapshotId"),true,environment,registries.records(),tags.records(),recipes.records(),mods.records(),diagnostics);
                connected();return result;
            }catch(Exception failure){fail(failure);throw new AssertionError("unreachable");}
            finally{if(status==CompanionConnectionStatus.SYNCHRONIZING){closeSocket();state(cancelled?CompanionConnectionStatus.DISCONNECTED:CompanionConnectionStatus.ERROR);}}
        },worker);
    }
    private <T> RecordTransfer.Received<T> dataset(final MessageType request,final MessageType response,final Class<T> type,final Consumer<String> progress,final long deadline,final long byteBudget)throws IOException {
        if(cancelled)throw new InterruptedIOException("Synchronization cancelled");progress.accept("Receiving "+request.name());final String id=UUID.randomUUID().toString();codec.write(socket.getOutputStream(),new ProtocolMessage(request,id,new JsonObject()));
        return new RecordTransfer(byteBudget).receive(()->{if(cancelled||System.nanoTime()>deadline)throw new SocketTimeoutException("Synchronization deadline exceeded");return codec.read(socket.getInputStream());},id,response,type,p->progress.accept(p.has("stage")?p.get("stage").getAsString():"Synchronizing"));
    }
    private JsonObject request(final MessageType type,final JsonObject payload,final MessageType expected)throws IOException {
        final String id=UUID.randomUUID().toString();codec.write(socket.getOutputStream(),new ProtocolMessage(type,id,payload));final var response=codec.read(socket.getInputStream());if(!response.requestId().equals(id)||response.messageType()!=expected)throw new ProtocolException("Unexpected response to "+type);return response.payload();
    }
    private synchronized void connected(){if(cancelled||socket==null||socket.isClosed())throw new CancellationException("Connection cancelled");state(CompanionConnectionStatus.CONNECTED);}
    private void fail(final Exception failure){closeSocket();state(cancelled?CompanionConnectionStatus.DISCONNECTED:CompanionConnectionStatus.ERROR);throw new CompletionException(cancelled?new CancellationException("Synchronization cancelled"):failure);}
    public synchronized void disconnect(){cancelled=true;closeSocket();state(CompanionConnectionStatus.DISCONNECTED);}
    public void cancelSynchronization(){disconnect();}
    private void closeSocket(){final var current=socket;socket=null;if(current!=null)try{current.close();}catch(IOException ignored){}}
    @Override public void close(){disconnect();heartbeat.shutdownNow();worker.shutdownNow();}
}
