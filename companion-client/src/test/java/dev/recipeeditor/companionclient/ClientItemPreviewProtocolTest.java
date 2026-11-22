package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionserver.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.nio.file.Path;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class ClientItemPreviewProtocolTest {
    private SnapshotProvider snapshots(){return new SnapshotProvider(){public long generation(){return 1;}public boolean ready(){return false;}public CompletableFuture<RuntimeData.Snapshot> capture(String session,BooleanSupplier cancelled,java.util.function.Consumer<String> progress){return CompletableFuture.completedFuture(new RuntimeFixtures().snapshot(Path.of("fixture"),session,1));}};}
    private ItemPreviewResponse image(final ItemPreviewRequest request)throws IOException{final var image=new BufferedImage(request.size(),request.size(),BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,request.nbt().isEmpty()?0xff00ff00:0xffff0000);final var bytes=new ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);return new ItemPreviewResponse(request,"MINECRAFT_CLIENT_INVENTORY","READY","packs-1",Base64.getEncoder().encodeToString(bytes.toByteArray()),"test transport fixture; not actual Minecraft rendering");}
    private ItemPreviewProvider previews(){return new ItemPreviewProvider(){public boolean available(){return true;}public String context(){return "packs-1";}public CompletableFuture<List<ItemPreviewResponse>> capture(final List<ItemPreviewRequest> requests,final BooleanSupplier cancelled){try{final var result=new ArrayList<ItemPreviewResponse>();for(final var r:requests)result.add(image(r));return CompletableFuture.completedFuture(result);}catch(IOException error){return CompletableFuture.failedFuture(error);}}};}
    @Test void capabilityNegotiatesBatchAndNbtWithoutRequiringAWorld()throws Exception{
        try(final var server=new ReadOnlyCompanionServer(snapshots(),previews());final var client=new CompanionConnectionService()){
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);assertTrue(client.itemPreviewsAvailable());
            final var requests=List.of(new ItemPreviewRequest("test:machine",64),new ItemPreviewRequest("test:machine",64,"nbt","{color:1}"),new ItemPreviewRequest("minecraft:stone",32));
            final var response=client.itemPreviews(requests).get(5,TimeUnit.SECONDS);assertEquals(requests,response.stream().map(ItemPreviewResponse::request).toList());assertFalse(Arrays.equals(response.get(0).png(),response.get(1).png()));assertEquals("packs-1",client.previewContext());assertEquals(CompanionConnectionStatus.CONNECTED,client.status());
        }
    }
    @Test void olderOrDedicatedCompanionCannotClaimClientCapture()throws Exception{
        try(final var server=new ReadOnlyCompanionServer(snapshots());final var client=new CompanionConnectionService()){
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);assertFalse(client.itemPreviewsAvailable());assertThrows(ExecutionException.class,()->client.itemPreviews(List.of(new ItemPreviewRequest("minecraft:stone",64))).get(2,TimeUnit.SECONDS));assertEquals(CompanionConnectionStatus.CONNECTED,client.status());
        }
    }
    @Test void wrongResourceIdentityCannotBecomeAnotherItemsCachedImage()throws Exception{
        final var broken=new ItemPreviewProvider(){public boolean available(){return true;}public String context(){return "packs-1";}public CompletableFuture<List<ItemPreviewResponse>> capture(List<ItemPreviewRequest> requests,BooleanSupplier cancelled){try{return CompletableFuture.completedFuture(List.of(image(new ItemPreviewRequest("wrong:neighbor",64))));}catch(IOException e){return CompletableFuture.failedFuture(e);}}};
        try(final var server=new ReadOnlyCompanionServer(snapshots(),broken);final var client=new CompanionConnectionService()){
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);assertThrows(ExecutionException.class,()->client.itemPreviews(List.of(new ItemPreviewRequest("minecraft:stone",64))).get(5,TimeUnit.SECONDS));assertEquals(CompanionConnectionStatus.CONNECTED,client.status());
        }
    }
    @Test void queueIsBoundedAndCancelledQueuedIconDoesNotReachRenderer()throws Exception{
        final var entered=new CountDownLatch(1);final var release=new CompletableFuture<List<ItemPreviewResponse>>();final var calls=new AtomicInteger();
        final var delayed=new ItemPreviewProvider(){public boolean available(){return true;}public String context(){return "packs-1";}public CompletableFuture<List<ItemPreviewResponse>> capture(List<ItemPreviewRequest> requests,BooleanSupplier cancelled){calls.incrementAndGet();entered.countDown();return release;}};
        try(final var server=new ReadOnlyCompanionServer(snapshots(),delayed);final var client=new CompanionConnectionService()){
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);final var request=new ItemPreviewRequest("test:machine",64);final var futures=new ArrayList<CompletableFuture<List<ItemPreviewResponse>>>();
            for(int i=0;i<8;i++)futures.add(client.itemPreviews(List.of(request)));assertTrue(entered.await(3,TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class,assertThrows(ExecutionException.class,()->client.itemPreviews(List.of(request)).get(1,TimeUnit.SECONDS)).getCause());futures.getLast().cancel(false);release.complete(List.of(image(request)));for(int i=0;i<7;i++)assertEquals(request,futures.get(i).get(5,TimeUnit.SECONDS).getFirst().request());assertEquals(7,calls.get());
        }
    }
    @Test void disconnectCompletesPendingPreviewWithoutWaitingForServerTimeout()throws Exception{
        final var entered=new CountDownLatch(1);final var delayed=new ItemPreviewProvider(){public boolean available(){return true;}public String context(){return "packs-1";}public CompletableFuture<List<ItemPreviewResponse>> capture(List<ItemPreviewRequest> requests,BooleanSupplier cancelled){entered.countDown();return new CompletableFuture<>();}};
        try(final var server=new ReadOnlyCompanionServer(snapshots(),delayed);final var client=new CompanionConnectionService()){
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);final var future=client.itemPreviews(List.of(new ItemPreviewRequest("test:machine",64)));assertTrue(entered.await(3,TimeUnit.SECONDS));client.disconnect();assertTrue(future.isDone());assertEquals(CompanionConnectionStatus.DISCONNECTED,client.status());
        }
    }
}
