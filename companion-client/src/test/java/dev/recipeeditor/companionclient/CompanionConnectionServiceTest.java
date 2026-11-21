package dev.recipeeditor.companionclient;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionserver.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("integration")
final class CompanionConnectionServiceTest {
    @TempDir Path source;
    private SnapshotProvider provider(){return new SnapshotProvider(){public long generation(){return 1;}public boolean ready(){return true;}public CompletableFuture<RuntimeData.Snapshot> capture(final String session,final java.util.function.BooleanSupplier cancelled,final java.util.function.Consumer<String> progress){return CompletableFuture.completedFuture(new RuntimeFixtures().snapshot(source,session,1));}};}
    @Test void authenticatesSynchronizesAndReconnectsOverActualTcp()throws Exception {try(final var server=new ReadOnlyCompanionServer(provider());final var client=new CompanionConnectionService()){server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);assertEquals(CompanionConnectionStatus.CONNECTED,client.status());final var data=client.synchronize(x->{}).get(5,TimeUnit.SECONDS);assertEquals(1,data.recipes().size());assertTrue(data.complete());client.disconnect();assertEquals(CompanionConnectionStatus.DISCONNECTED,client.status());Thread.sleep(100);client.connect(server.credentials()).get(5,TimeUnit.SECONDS);assertTrue(client.synchronize(x->{}).get(5,TimeUnit.SECONDS).complete());}}
    @Test void wrongTokenFailsMutualAuthenticationWithoutSharingToken()throws Exception {try(final var server=new ReadOnlyCompanionServer(provider());final var client=new CompanionConnectionService()){server.start();final var c=server.credentials();assertThrows(ExecutionException.class,()->client.connect(new SessionCredentials(1,c.host(),c.port(),SessionCredentials.nonce(),c.sessionId())).get(5,TimeUnit.SECONDS));assertEquals(CompanionConnectionStatus.ERROR,client.status());}}
    @Test void connectionLossFailsRequestAndLeavesNoCompleteSnapshot()throws Exception {final var server=new ReadOnlyCompanionServer(provider());try(final var client=new CompanionConnectionService()){server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);server.close();assertThrows(ExecutionException.class,()->client.synchronize(x->{}).get(5,TimeUnit.SECONDS));assertEquals(CompanionConnectionStatus.ERROR,client.status());}finally{server.close();}}
    @Test void cancellationReleasesBlockedCollection()throws Exception {final var entered=new CountDownLatch(1);final var capture=new CompletableFuture<RuntimeData.Snapshot>();final var provider=new SnapshotProvider(){public long generation(){return 1;}public boolean ready(){return true;}public CompletableFuture<RuntimeData.Snapshot> capture(String session,java.util.function.BooleanSupplier cancelled,java.util.function.Consumer<String> p){entered.countDown();return capture;}};try(final var server=new ReadOnlyCompanionServer(provider);final var client=new CompanionConnectionService()){server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);final var future=client.synchronize(x->{});assertTrue(entered.await(3,TimeUnit.SECONDS));client.cancelSynchronization();assertThrows(ExecutionException.class,()->future.get(3,TimeUnit.SECONDS));assertEquals(CompanionConnectionStatus.DISCONNECTED,client.status());}}
    @Test void unknownProtocolAndStalledHandshakeAreRejected()throws Exception {try(final var listener=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));final var client=new CompanionConnectionService()){final var task=CompletableFuture.runAsync(()->{try(final var socket=listener.accept()){new ProtocolCodec().read(socket.getInputStream());final var out=new java.io.DataOutputStream(socket.getOutputStream());final byte[] bad="{\"protocolVersion\":2,\"messageType\":\"HANDSHAKE_RESPONSE\",\"requestId\":\"x\",\"payload\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(bad.length);out.write(bad);}catch(java.io.IOException failure){throw new CompletionException(failure);}});assertThrows(ExecutionException.class,()->client.connect(new SessionCredentials(1,"127.0.0.1",listener.getLocalPort(),SessionCredentials.nonce(),"test")).get(8,TimeUnit.SECONDS));task.get(5,TimeUnit.SECONDS);}}
    @Test void keepaliveProtectsSlowCollectionAndDoesNotMixPongWithDatasetFrames() throws Exception {
        final var slow=new SnapshotProvider() {
            public long generation(){return 1;}
            public boolean ready(){return true;}
            public CompletableFuture<RuntimeData.Snapshot> capture(final String session,final java.util.function.BooleanSupplier cancelled,final java.util.function.Consumer<String> progress) {
                return CompletableFuture.supplyAsync(()->new RuntimeFixtures().snapshot(source,session,1),CompletableFuture.delayedExecutor(3500,TimeUnit.MILLISECONDS));
            }
        };
        try(final var server=new ReadOnlyCompanionServer(slow,2000);final var client=new CompanionConnectionService(java.time.Duration.ofMillis(100))) {
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);
            final var result=client.synchronize(p->{}).get(9,TimeUnit.SECONDS);
            assertTrue(result.complete());assertEquals(1,result.recipes().size());assertEquals(CompanionConnectionStatus.CONNECTED,client.status());
        }
    }
}
