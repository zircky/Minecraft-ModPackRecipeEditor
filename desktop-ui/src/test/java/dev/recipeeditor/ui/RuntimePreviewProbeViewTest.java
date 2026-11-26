package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionserver.*;
import dev.recipeeditor.ui.rendering.InventoryPreviewProvider;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real authenticated TCP and production diagnostic view; PNG source is an explicitly synthetic transport fixture. */
@Tag("integration")
final class RuntimePreviewProbeViewTest {
    private SnapshotProvider snapshots(){return new SnapshotProvider(){public boolean ready(){return false;}public long generation(){return 1;}public CompletableFuture<RuntimeData.Snapshot> capture(String id,java.util.function.BooleanSupplier cancelled,java.util.function.Consumer<String> progress){return CompletableFuture.failedFuture(new IllegalStateException("No world in fixture"));}};}
    @Test void requestButtonShowsActualConnectionFailureThenTransferredPngInImageView()throws Exception{
        final var fixture=new InventorySurfaceTestSupport.Client();
        final var previews=new ItemPreviewProvider(){public boolean available(){return true;}public String context(){return fixture.previewContext();}public CompletableFuture<List<ItemPreviewResponse>> capture(List<ItemPreviewRequest> items,java.util.function.BooleanSupplier cancelled){return fixture.itemPreviews(items);}};
        final var stage=new AtomicReference<Stage>();final var view=new AtomicReference<RuntimeIntegrationView>();
        try(final var server=new ReadOnlyCompanionServer(snapshots(),previews);final var client=new CompanionConnectionService();
                final var inventory=new InventoryPreviewProvider()){
            server.start();inventory.client(client);
            FxLayoutTestSupport.run(()->{javafx.application.Platform.setImplicitExit(false);final var pane=new RuntimeIntegrationView(client,new RuntimeSnapshotService(),(data,file)->{},inventory);view.set(pane);final var window=new Stage();stage.set(window);window.setScene(new Scene(pane,1100,750));window.show();assertEquals("gtceu:aluminium_block",((TextField)pane.lookup("#client-preview-item-id")).getText());((Button)pane.lookup("#request-client-preview")).fire();});
            final long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);final var unavailable=new AtomicBoolean();do{FxLayoutTestSupport.run(()->unavailable.set(((Label)view.get().lookup("#client-preview-trace")).getText().contains("CLIENT_DISCONNECTED")));if(!unavailable.get())Thread.sleep(10);}while(!unavailable.get()&&System.nanoTime()<end);assertTrue(unavailable.get());
            client.connect(server.credentials()).get(5,TimeUnit.SECONDS);FxLayoutTestSupport.run(()->((Button)view.get().lookup("#request-client-preview")).fire());
            final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);final var available=new AtomicBoolean();do{FxLayoutTestSupport.run(()->available.set(((ImageView)view.get().lookup("#client-preview-image")).getImage()!=null&&((Label)view.get().lookup("#client-preview-trace")).getText().contains("PREVIEW_AVAILABLE")));if(!available.get())Thread.sleep(10);}while(!available.get()&&System.nanoTime()<deadline);
            assertTrue(available.get());assertEquals("gtceu:aluminium_block",fixture.requests.getFirst().resourceId());assertTrue(client.previewStatistics().contains("sent=1"));assertTrue(client.previewStatistics().contains("PNG transferred=1"));
            client.disconnect();
            FxLayoutTestSupport.run(()->{assertNull(((ImageView)view.get().lookup("#client-preview-image")).getImage());assertTrue(((Label)view.get().lookup("#client-preview-trace")).getText().contains("CLIENT_DISCONNECTED"));});
        }finally{FxLayoutTestSupport.run(()->{if(view.get()!=null)view.get().close();if(stage.get()!=null)stage.get().close();});}
    }
    @Test void authenticatedServerWithoutClientCapabilityCannotBeReportedAsRendered()throws Exception{
        try(final var server=new ReadOnlyCompanionServer(snapshots());final var client=new CompanionConnectionService()){
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);InventoryPreviewProvider.shared().client(client);
            assertEquals(CompanionConnectionStatus.CONNECTED,client.status());assertTrue(client.previewUnavailableReason().contains("CLIENT_PREVIEW_CAPABILITY_MISSING"));
            final var preview=InventoryPreviewProvider.shared().requestClientPreview("gtceu:aluminium_block",128).get(3,TimeUnit.SECONDS);assertEquals(0,preview.png().length);assertTrue(preview.diagnostic().contains("CAPABILITY_MISSING"));
        }finally{InventoryPreviewProvider.shared().client(null);}
    }
}
