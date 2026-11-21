package dev.recipeeditor.companionserver;

import dev.recipeeditor.companionprotocol.*;
import com.google.gson.JsonObject;
import java.net.*;
import dev.recipeeditor.companionprotocol.ProtocolException;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Fixed read-only command set, one authenticated session and bounded worker queues. */
public final class ReadOnlyCompanionServer implements AutoCloseable {
    private final SnapshotProvider provider;
    private final ServerSocket listener;
    private final SessionCredentials credentials;
    private final ExecutorService acceptor=Executors.newSingleThreadExecutor(daemon("mre-accept"));
    private final ExecutorService reader=Executors.newSingleThreadExecutor(daemon("mre-read"));
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),daemon("mre-send"));
    private final AtomicReference<Socket> active=new AtomicReference<>();
    private volatile boolean closed;
    private final int readTimeoutMillis;
    public ReadOnlyCompanionServer(final SnapshotProvider provider)throws IOException {
        this(provider,360000);
    }
    public ReadOnlyCompanionServer(final SnapshotProvider provider,final int readTimeoutMillis)throws IOException {
        if(readTimeoutMillis<1)throw new IllegalArgumentException("Positive read timeout required");this.readTimeoutMillis=readTimeoutMillis;
        this.provider=Objects.requireNonNull(provider);listener=new ServerSocket();listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),1);
        credentials=new SessionCredentials(1,"127.0.0.1",listener.getLocalPort(),SessionCredentials.nonce(),UUID.randomUUID().toString());
    }
    public SessionCredentials credentials(){return credentials;}
    public void start(){acceptor.submit(()->{while(!closed)try{final var socket=listener.accept();if(!active.compareAndSet(null,socket)){socket.close();continue;}reader.submit(()->serve(socket));}catch(IOException ended){if(!closed)close();}});}
    private void serve(final Socket socket) {
        final var cancelled=new AtomicBoolean();final var inFlight=new AtomicReference<String>();final var snapshot=new AtomicReference<RuntimeData.Snapshot>();final var pending=new AtomicReference<CompletableFuture<RuntimeData.Snapshot>>();
        try(socket) {
            socket.setSoTimeout(5000);final var codec=new ProtocolCodec();final var input=socket.getInputStream();final var output=socket.getOutputStream();
            authenticate(codec,input,output);socket.setSoTimeout(readTimeoutMillis);
            while(!closed&&!cancelled.get()) {
                final var request=codec.read(input);
                if(request.messageType()==MessageType.CANCEL||request.messageType()==MessageType.DISCONNECT){cancelled.set(true);break;}
                if(request.payload().size()!=0) {error(codec,output,request,"INVALID_REQUEST","Read-only requests accept no arguments");continue;}
                if(request.messageType()==MessageType.PING){final var p=new JsonObject();p.addProperty("generation",provider.generation());p.addProperty("ready",provider.ready());codec.write(output,new ProtocolMessage(MessageType.PONG,request.requestId(),p));continue;}
                if(!Set.of(MessageType.GET_REGISTRIES,MessageType.GET_TAGS,MessageType.GET_RECIPES,MessageType.GET_MOD_LIST,MessageType.GET_MATERIALS).contains(request.messageType())){error(codec,output,request,"UNSUPPORTED_REQUEST","Command is not available");continue;}
                if(!inFlight.compareAndSet(null,request.requestId())){error(codec,output,request,"BUSY","One synchronization request at a time");continue;}
                worker.execute(()->{
                    try {
                        if(request.messageType()==MessageType.GET_REGISTRIES) {
                            final var collectingStage=new AtomicReference<String>("Collecting on the server thread");
                            final var capture=provider.capture(credentials.sessionId(),cancelled::get,collectingStage::set);pending.set(capture);
                            final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(300);RuntimeData.Snapshot captured;
                            while(true){if(cancelled.get()||System.nanoTime()>deadline){capture.cancel(false);throw new IOException("Collection cancelled/timed out");}try{captured=capture.get(500,TimeUnit.MILLISECONDS);break;}catch(TimeoutException waiting){final var p=new JsonObject();p.addProperty("stage",collectingStage.get());codec.write(output,new ProtocolMessage(MessageType.SYNC_PROGRESS,request.requestId(),p));}}
                            pending.set(null);
                            if(!captured.complete())throw new IOException("Collector returned partial snapshot");snapshot.set(captured);
                        }
                        final var data=snapshot.get();if(data==null)throw new IOException("Request registries first");
                        if(!provider.ready()||provider.generation()!=data.environment().generation())throw new IOException("Snapshot is stale after reload/world change; synchronize again");
                        final var meta=new JsonObject();meta.add("environment",ProtocolCodec.GSON.toJsonTree(data.environment()));meta.add("diagnostics",ProtocolCodec.GSON.toJsonTree(data.diagnostics()));
                        final Iterable<?> rows;final MessageType response;
                        switch(request.messageType()) {
                            case GET_REGISTRIES -> {rows=data.registries();response=MessageType.REGISTRY_DATA;}
                            case GET_TAGS -> {rows=data.tags();response=MessageType.TAG_DATA;}
                            case GET_RECIPES -> {rows=data.recipes();response=MessageType.RECIPE_DATA;}
                            case GET_MOD_LIST -> {rows=data.mods();response=MessageType.MOD_LIST_DATA;}
                            case GET_MATERIALS -> {rows=data.materials();response=MessageType.MATERIAL_DATA;}
                            default -> throw new IOException("Unsupported request");
                        }
                        new RecordTransfer().send(message->{if(message.messageType()==MessageType.SYNC_COMPLETED)inFlight.compareAndSet(request.requestId(),null);codec.write(output,message);},request.requestId(),response,data.snapshotId(),rows,meta,()->cancelled.get()||!provider.ready()||provider.generation()!=data.environment().generation());
                    }catch(Exception failure){try{error(codec,output,request,"SYNC_FAILED",safeMessage(failure));}catch(IOException gone){cancelled.set(true);}}
                    finally{inFlight.compareAndSet(request.requestId(),null);}
                });
            }
        }catch(IOException|RuntimeException ended){cancelled.set(true);}
        finally{cancelled.set(true);final var capture=pending.getAndSet(null);if(capture!=null)capture.cancel(false);active.compareAndSet(socket,null);}
    }
    private void authenticate(final ProtocolCodec codec,final InputStream input,final OutputStream output)throws IOException {
        final var hello=codec.read(input);if(hello.messageType()!=MessageType.HANDSHAKE||!hello.payload().keySet().equals(Set.of("clientNonce")))throw new ProtocolException("Handshake required");
        final String client=ProtocolCodec.required(hello.payload(),"clientNonce"),server=SessionCredentials.nonce();final var challenge=new JsonObject();challenge.addProperty("serverNonce",server);challenge.addProperty("sessionId",credentials.sessionId());challenge.addProperty("proof",credentials.proof("server",client,server));codec.write(output,new ProtocolMessage(MessageType.HANDSHAKE_RESPONSE,hello.requestId(),challenge));
        final var auth=codec.read(input);if(auth.messageType()!=MessageType.HANDSHAKE||!auth.payload().keySet().equals(Set.of("proof"))||!credentials.matches(credentials.proof("client",client,server),ProtocolCodec.required(auth.payload(),"proof")))throw new ProtocolException("Authentication failed");
        final var ok=new JsonObject();ok.addProperty("authenticated",true);ok.addProperty("companionVersion",CompanionIdentity.version());ok.add("capabilities",ProtocolCodec.GSON.toJsonTree(List.of("materials-v1","resource-visuals-v1")));codec.write(output,new ProtocolMessage(MessageType.HANDSHAKE_RESPONSE,auth.requestId(),ok));
    }
    private void error(final ProtocolCodec codec,final OutputStream output,final ProtocolMessage request,final String code,final String message)throws IOException {final var payload=new JsonObject();payload.addProperty("code",code);payload.addProperty("message",message);codec.write(output,new ProtocolMessage(MessageType.ERROR,request.requestId(),payload));}
    private String safeMessage(final Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();return cause.getClass().getSimpleName()+": "+Objects.toString(cause.getMessage(),"Synchronization failed").replace(credentials.token(),"[redacted]");}
    private static ThreadFactory daemon(final String name){return job->{final var thread=new Thread(job,name);thread.setDaemon(true);return thread;};}
    @Override public void close(){closed=true;try{listener.close();}catch(IOException ignored){}final var socket=active.getAndSet(null);if(socket!=null)try{socket.close();}catch(IOException ignored){}acceptor.shutdownNow();reader.shutdownNow();worker.shutdownNow();}
}
