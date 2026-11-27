package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.companionclient.CompanionConnectionService;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionserver.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class PreviewCachePersistenceTest {
    @TempDir Path directory;

    @Test void collectsMissingPreviewsAndReopensNativeAlphaOffline() throws Exception {
        final var captures=new java.util.concurrent.atomic.AtomicInteger();
        final var bridge=new ItemPreviewProvider(){
            public boolean available(){return true;}
            public String context(){return "fixture-pack-context";}
            public CompletableFuture<List<ItemPreviewResponse>> capture(final List<ItemPreviewRequest> rows,
                    final java.util.function.BooleanSupplier cancelled){
                captures.addAndGet(rows.size());
                return CompletableFuture.completedFuture(rows.stream().map(row->new ItemPreviewResponse(row,
                        "MINECRAFT_CLIENT_INVENTORY","READY",context(),Base64.getEncoder().encodeToString(png(row.size())),"fixture")).toList());
            }
        };
        final var snapshots=new SnapshotProvider(){
            public boolean ready(){return false;}
            public long generation(){return 0;}
            public CompletableFuture<RuntimeData.Snapshot> capture(final String id,final java.util.function.BooleanSupplier cancel,
                    final java.util.function.Consumer<String> progress){throw new UnsupportedOperationException();}
        };
        final var item=entry();
        final RuntimeData.Snapshot snapshot;
        try(final var server=new ReadOnlyCompanionServer(snapshots,bridge);final var client=new CompanionConnectionService();
                final var provider=new InventoryPreviewProvider(directory)){
            server.start();client.connect(server.credentials()).get(5,TimeUnit.SECONDS);
            snapshot=new RuntimeData.Snapshot(2,"capture",true,new RuntimeData.Environment(server.credentials().sessionId(),
                    directory.toString(),"1.20.1","FORGE","47","0.1",0,"2026-11-27"),List.of(),List.of(),List.of(),List.of(),List.of());
            final var index=new RegistryIndex(List.of(item));
            provider.client(client);provider.bindSnapshot(index,snapshot,"fixture");
            assertEquals(1,provider.collect(InventoryPreviewProvider.CollectionMode.MISSING,()->false,()->false,ignored->{}).cached());
            assertEquals(1,provider.collect(InventoryPreviewProvider.CollectionMode.MISSING,()->false,()->false,ignored->{}).cached());
            assertEquals(1,captures.get());
            assertEquals("PERSISTENT_PREVIEW_CACHE",provider.preview(item,index,directory,64).get(5,TimeUnit.SECONDS).source());
        }
        try(final var reopened=new InventoryPreviewProvider(directory)){
            final var index=new RegistryIndex(List.of(item));
            reopened.bindSnapshot(index,snapshot,"fixture");
            final var result=reopened.preview(item,index,directory,128).get(5,TimeUnit.SECONDS);
            assertEquals("PERSISTENT_PREVIEW_CACHE",result.source());
            final var decoded=ImageIO.read(new ByteArrayInputStream(result.png()));
            assertEquals(0,decoded.getRGB(0,0)>>>24);
            assertEquals(0xff12abcd,decoded.getRGB(31,31));
            assertEquals("PLACEHOLDER",reopened.preview(item,index,directory,64,"{Damage:1}").get(5,TimeUnit.SECONDS).source());
        }
    }

    @Test void detectsCorruptionAndSeparatesPackContexts() throws Exception {
        final var store=new PersistentItemPreviews(directory);
        store.write("pack-a","variant","resources-a",64,png(64),"");
        assertEquals(PersistentItemPreviews.Status.STALE,store.read("pack-a","variant",64,"resources-b").status());
        assertEquals(PersistentItemPreviews.Status.NOT_COLLECTED,store.read("pack-b","variant",64,null).status());
        final Path record;
        try(final var files=Files.walk(directory)){record=files.filter(Files::isRegularFile).findFirst().orElseThrow();}
        Files.write(record,new byte[]{1,2,3});
        assertEquals(PersistentItemPreviews.Status.CORRUPTED,store.read("pack-a","variant",64,null).status());
        store.write("pack-a","variant","resources-a",64,png(64),"");
        assertEquals(PersistentItemPreviews.Status.READY,store.read("pack-a","variant",64,null).status());
    }

    @Test void projectedSideSamplesNeverReplaceGeometryButRecordedFlatLayersRemainAvailable() throws Exception {
        Files.write(directory.resolve("sprite.png"),png(64));
        final var source=new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),directory,"sprite.png","fixture");
        final var visual=new ResourceVisual(ResourceVisual.Status.READY,List.of(new ResourceVisual.Layer(source,null)),"fixture sprite");
        final var side=previewEntry("test:pipe","SAMPLE",visual);
        final var flat=previewEntry("test:plate","LAYERS",visual);
        final var index=new RegistryIndex(List.of(side,flat));
        try(final var provider=new InventoryPreviewProvider(directory.resolve("cache"))){
            assertEquals("PLACEHOLDER",provider.preview(side,index,directory,64).get(5,TimeUnit.SECONDS).source());
            assertEquals("STATIC_TEXTURE",provider.preview(flat,index,directory,64).get(5,TimeUnit.SECONDS).source());
        }
    }
    private RegistryEntry previewEntry(final String id,final String mode,final ResourceVisual visual){
        return new RegistryEntry(ResourceLocation.parse(id),ResourceKind.ITEM,id,"",Map.of(),List.of(),List.of(),
                DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"fixture",visual,Map.of("previewMode",mode));
    }

    private RegistryEntry entry(){
        return new RegistryEntry(ResourceLocation.parse("test:native"),ResourceKind.ITEM,"Native","",Map.of(),List.of(),List.of(),
                DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"fixture");
    }
    private byte[] png(final int size){
        try {
            final var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            image.setRGB(31,31,0xff12abcd);
            final var bytes=new ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);return bytes.toByteArray();
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
}
