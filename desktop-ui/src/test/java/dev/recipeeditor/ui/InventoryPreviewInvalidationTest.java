package dev.recipeeditor.ui;

import dev.recipeeditor.registry.*;
import dev.recipeeditor.ui.rendering.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class InventoryPreviewInvalidationTest {
    @TempDir Path root;
    @Test void technicalBlockUsesOnlyAnExplicitCapturedBlockItemRelationship()throws Exception{
        FxLayoutTestSupport.run(()->javafx.application.Platform.setImplicitExit(false));
        final var blockId=dev.recipeeditor.core.ResourceLocation.parse("test:technical_block");final var itemId=dev.recipeeditor.core.ResourceLocation.parse("test:different_item_id");
        final var capture=new ResourceSource(SourceType.RUNTIME,List.of(),root.resolve("capture.mresnapshot"),"snapshot/capture-1/registry","fixture");
        final var visual=ResourceVisual.legacy(List.of());
        final var block=new RegistryEntry(blockId,ResourceKind.BLOCK,"Block","",Map.of(),List.of(capture),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"fixture",visual,Map.of("hasBlockItem","true"));
        final var item=new RegistryEntry(itemId,ResourceKind.ITEM,"Item","",Map.of(),List.of(capture),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"fixture",visual,Map.of("blockItem","true","blockId",blockId.toString()));
        final var client=new InventorySurfaceTestSupport.Client();
        try(final var provider=new InventoryPreviewProvider()){
            provider.client(client);assertEquals("MINECRAFT_CLIENT_INVENTORY",provider.preview(block,new RegistryIndex(List.of(item,block)),root,64).get(8,TimeUnit.SECONDS).source());assertEquals(itemId.toString(),client.requests.getFirst().resourceId());
            final var unknown=provider.preview(block,new RegistryIndex(List.of(block)),root,64).get(8,TimeUnit.SECONDS);assertEquals("PLACEHOLDER",unknown.source());assertEquals(1,client.calls.get(),"Do not send a block ID as an unverified item ID");
        }
    }
    @Test void cacheSeparatesSnapshotsRuntimeContextAndDisconnect()throws Exception{
        FxLayoutTestSupport.run(()->javafx.application.Platform.setImplicitExit(false));
        final var client=new InventorySurfaceTestSupport.Client();final var index=InventorySurfaceTestSupport.discovery().registry();final var entry=index.entries().getFirst();
        try(final var provider=new InventoryPreviewProvider()){
            provider.client(client);assertEquals("MINECRAFT_CLIENT_INVENTORY",provider.preview(entry,index,root,64).get(8,TimeUnit.SECONDS).source());
            provider.preview(entry,index,root,64).get(8,TimeUnit.SECONDS);assertEquals(1,client.calls.get());
            client.context=UUID.randomUUID().toString();provider.preview(entry,index,root,64).get(8,TimeUnit.SECONDS);assertEquals(2,client.calls.get());
            provider.preview(entry,new RegistryIndex(List.of(entry)),root,64).get(8,TimeUnit.SECONDS);assertEquals(3,client.calls.get());
            client.available=false;final var offline=provider.preview(entry,index,root,64).get(8,TimeUnit.SECONDS);assertEquals("PLACEHOLDER",offline.source());assertTrue(offline.diagnostic().contains("MISSING_MODEL"));
        }
    }
    @Test void changingNestedResourcePackFileInvalidatesPreviouslyCapturedImage()throws Exception{
        FxLayoutTestSupport.run(()->javafx.application.Platform.setImplicitExit(false));
        final Path file=root.resolve("assets/test/textures/item/icon.png");Files.createDirectories(file.getParent());Files.writeString(file,"first");
        final var source=new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),root,"assets/test/textures/item/icon.png","fixture");
        final var entry=InventorySurfaceTestSupport.discovery().registry().entries().getFirst();
        final var index=new RegistryIndex(List.of(entry),new ResourceScanResult(List.of(),List.of(source),List.of()));
        try(final var provider=new InventoryPreviewProvider()){
            provider.client(new InventorySurfaceTestSupport.Client());assertEquals("MINECRAFT_CLIENT_INVENTORY",provider.preview(entry,index,root,64).get(8,TimeUnit.SECONDS).source());
            Files.writeString(file,"changed pack contents");
            final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);ItemPreviewCache.Image result;
            do{Thread.sleep(100);result=provider.preview(entry,index,root,64).get(8,TimeUnit.SECONDS);}while(!result.source().equals("PLACEHOLDER")&&System.nanoTime()<deadline);
            assertEquals("PLACEHOLDER",result.source());assertTrue(result.diagnostic().contains("re-index"));
        }
    }
}
