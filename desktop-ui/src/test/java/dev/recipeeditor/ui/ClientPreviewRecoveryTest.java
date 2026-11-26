package dev.recipeeditor.ui;

import dev.recipeeditor.registry.*;
import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionserver.*;
import dev.recipeeditor.ui.rendering.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.scene.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class ClientPreviewRecoveryTest {
    @TempDir Path root;
    @Test void successfulStaticFallbackMustNotPoisonNativeCacheKey()throws Exception{
        FxLayoutTestSupport.run(()->javafx.application.Platform.setImplicitExit(false));
        final var id=dev.recipeeditor.core.ResourceLocation.parse("test:inventory_icon");
        final var texture=new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),root,"assets/test/textures/item/icon.png","synthetic fixture");
        final Path png=root.resolve(texture.internalPath());Files.createDirectories(png.getParent());final var pixels=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);pixels.setRGB(8,8,0xffffffff);javax.imageio.ImageIO.write(pixels,"PNG",png.toFile());
        final var source=new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),root,"assets/test/models/item/inventory_icon.json","synthetic fixture");
        final var model=new ResourceDocument(source,"{\"parent\":\"builtin/generated\",\"textures\":{\"layer0\":\"test:item/icon\"}}");
        final var entry=InventorySurfaceTestSupport.discovery().registry().entries().getFirst();final var index=new RegistryIndex(List.of(entry),new ResourceScanResult(List.of(model),List.of(texture),List.of()));
        final var fixture=new InventorySurfaceTestSupport.Client();final var calls=new AtomicInteger();
        final var client=new ItemPreviewClient(){public boolean itemPreviewsAvailable(){return true;}public String previewContext(){return fixture.previewContext();}public CompletableFuture<List<ItemPreviewResponse>> itemPreviews(List<ItemPreviewRequest> items){return calls.incrementAndGet()==1?CompletableFuture.completedFuture(List.of(ItemPreviewResponse.error(items.getFirst(),previewContext(),"Temporary synthetic renderer failure"))):fixture.itemPreviews(items);}};
        try(final var provider=new InventoryPreviewProvider()){
            provider.client(client);assertEquals("STATIC_ITEM_MODEL_2D",provider.preview(entry,index,root,64).get(10,TimeUnit.SECONDS).source());
            assertEquals("MINECRAFT_CLIENT_INVENTORY",provider.preview(entry,index,root,64).get(10,TimeUnit.SECONDS).source());assertEquals(2,calls.get(),"Fallback must not satisfy a native cache lookup");
        }
    }
    @Test void realTcpConnectRefreshesAlreadyMountedPlaceholderAndProbeUsesSameClient()throws Exception{
        final var fixture=new InventorySurfaceTestSupport.Client();
        final var snapshots=new SnapshotProvider(){public boolean ready(){return false;}public long generation(){return 1;}public CompletableFuture<RuntimeData.Snapshot> capture(String session,java.util.function.BooleanSupplier cancelled,java.util.function.Consumer<String> progress){return CompletableFuture.failedFuture(new IllegalStateException("No world in transport fixture"));}};
        final var capture=new ItemPreviewProvider(){public boolean available(){return true;}public String context(){return fixture.previewContext();}public CompletableFuture<List<ItemPreviewResponse>> capture(List<ItemPreviewRequest> items,java.util.function.BooleanSupplier cancelled){return fixture.itemPreviews(items);}};
        final var stage=new AtomicReference<Stage>();final var card=new AtomicReference<ResourcePreview>();
        try(final var server=new ReadOnlyCompanionServer(snapshots,capture);final var client=new CompanionConnectionService();final var textures=new TextureResourceService()){
            server.start();InventoryPreviewProvider.shared().client(client);
            FxLayoutTestSupport.run(()->{javafx.application.Platform.setImplicitExit(false);final var data=InventorySurfaceTestSupport.discovery();final var preview=new ResourcePreview(data.registry().entries().getFirst(),data,textures,64,()->true,p->{});card.set(preview);final var window=new Stage();stage.set(window);window.setScene(new Scene(new StackPane(preview),200,200));window.show();});
            assertEquals("PLACEHOLDER",InventorySurfaceTestSupport.await(card.get()).source());
            client.connect(server.credentials()).get(5,TimeUnit.SECONDS);
            final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);final var available=new AtomicBoolean();
            do{FxLayoutTestSupport.run(()->available.set("PREVIEW_AVAILABLE".equals(card.get().getProperties().get("inventory.preview.status"))&&card.get().getChildren().stream().anyMatch(n->n instanceof ImageView)));if(!available.get())Thread.sleep(20);}while(!available.get()&&System.nanoTime()<deadline);
            assertTrue(available.get(),"Connect must replace an existing placeholder without rebuilding browser");
            final var probe=InventoryPreviewProvider.shared().requestClientPreview("gtceu:aluminium_block",128).get(5,TimeUnit.SECONDS);assertEquals("MINECRAFT_CLIENT_INVENTORY",probe.source());assertTrue(probe.png().length>0);
            client.disconnect();assertTrue(InventoryPreviewProvider.shared().requestClientPreview("gtceu:aluminium_block",128).get(2,TimeUnit.SECONDS).diagnostic().contains("CLIENT_DISCONNECTED"));
        }finally{FxLayoutTestSupport.run(()->{if(stage.get()!=null)stage.get().close();});InventoryPreviewProvider.shared().client(null);}
    }
}
