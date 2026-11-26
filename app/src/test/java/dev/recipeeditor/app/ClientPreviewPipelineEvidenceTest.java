package dev.recipeeditor.app;

import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.ui.ResourceBrowserView;
import dev.recipeeditor.ui.rendering.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Minecraft -> TCP -> shared cache -> production browser ImageViews; deliberately no static model assets. */
@Tag("external")
final class ClientPreviewPipelineEvidenceTest {
    private void fx(final Runnable action)throws Exception{final var done=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();done.complete(null);}catch(Throwable failure){done.completeExceptionally(failure);}});done.get(20,TimeUnit.SECONDS);}
    private List<ItemPreviewCache.Image> await(final ResourceBrowserView browser,final boolean nativeReady)throws Exception{
        final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);final var images=new ArrayList<ItemPreviewCache.Image>();
        do{fx(()->{browser.applyCss();browser.layout();images.clear();for(final var node:browser.lookupAll(".inventory-preview"))if(node.getProperties().get("inventory.preview") instanceof ItemPreviewCache.Image image)images.add(image);});
            if(images.size()>=2&&images.stream().allMatch(image->nativeReady?image.source().equals("MINECRAFT_CLIENT_INVENTORY")&&image.png().length>0:image.png().length==0))return List.copyOf(images);Thread.sleep(50);
        }while(System.nanoTime()<deadline);throw new AssertionError("Production browser did not reach "+(nativeReady?"native ImageViews":"placeholder")+": "+images.stream().map(ItemPreviewCache.Image::diagnostic).toList());
    }
    @Test @EnabledIfSystemProperty(named="previewSession",matches=".+")
    void realClientReplacesMissingModelsInMountedBrowserAndDisconnectRestoresExplicitFailure()throws Exception{
        final var ready=new CompletableFuture<Void>();try{Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException started){ready.complete(null);}ready.get(30,TimeUnit.SECONDS);
        final Path snapshotFile=Path.of(System.getProperty("phase19Snapshot"));final var snapshot=new RuntimeSnapshotService().load(snapshotFile);
        final var wanted=new LinkedHashSet<>(List.of("gtceu:aluminium_block","gtceu:aluminium_ingot","gtceu:aluminium_dust","gtceu:aluminium_plate","gtceu:aluminium_ore","botania:floating_daffomill"));
        final var merged=new RegistryMergeService().merge(new RegistryIndex(List.of()),snapshot,snapshotFile).preferred();
        final var entries=merged.entries(ResourceKind.ITEM).stream().filter(e->wanted.contains(e.id().toString())).toList();assertTrue(entries.stream().anyMatch(e->e.id().toString().equals("gtceu:aluminium_block")),"Target must be genuinely registered in supplied snapshot");
        final var index=new RegistryIndex(entries);final var data=new DiscoveryResult(Path.of(System.getProperty("realModpack")),index,new dev.recipeeditor.recipeengine.RecipeIndex(List.of(),new TagIndex(List.of())),List.of(),0);
        final var browser=new AtomicReference<ResourceBrowserView>();final var stage=new AtomicReference<Stage>();final var rows=new ArrayList<Map<String,Object>>();final Path output=Files.createDirectories(Path.of("build/phase21-evidence"));
        try(final var client=new CompanionConnectionService();final var textures=new TextureResourceService()){
            InventoryPreviewProvider.shared().client(client);
            fx(()->{final var view=new ResourceBrowserView(data,textures,q->{},(e,u)->false);browser.set(view);final var window=new Stage();stage.set(window);window.setTitle("Actual client preview pipeline verification");window.setScene(new Scene(view,1200,850));window.show();view.showResource(ResourceKind.ITEM,dev.recipeeditor.core.ResourceLocation.parse("gtceu:aluminium_block"));});
            final var missing=await(browser.get(),false);assertTrue(missing.stream().allMatch(i->i.diagnostic().contains("CLIENT_DISCONNECTED")),"Native reason must survive missing static model");
            client.connect(Path.of(System.getProperty("previewSession"))).get(10,TimeUnit.SECONDS);assertTrue(client.itemPreviewsAvailable(),client.previewUnavailableReason());
            for(final var id:wanted){if(entries.stream().noneMatch(e->e.id().toString().equals(id))){rows.add(Map.of("id",id,"status","NOT_REGISTERED_IN_SNAPSHOT"));continue;}
                if(!id.equals("gtceu:aluminium_block"))fx(()->browser.get().showResource(ResourceKind.ITEM,dev.recipeeditor.core.ResourceLocation.parse(id)));
                final var shown=await(browser.get(),true);final var preview=shown.stream().max(Comparator.comparingInt(i->i.png().length)).orElseThrow();
                final var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(preview.png()));assertNotNull(decoded);long visible=0;for(int y=0;y<decoded.getHeight();y++)for(int x=0;x<decoded.getWidth();x++)if((decoded.getRGB(x,y)>>>24)>0)visible++;assertTrue(visible>20,id+" is blank");
                Files.write(output.resolve(id.replace(':','_')+".png"),preview.png());rows.add(Map.of("id",id,"status","PREVIEW_AVAILABLE","source",preview.source(),"imageViews",shown.size(),"visiblePixels",visible));
            }
            client.disconnect();final var offline=await(browser.get(),false);assertTrue(offline.stream().allMatch(i->i.diagnostic().contains("CLIENT_DISCONNECTED")));
            Files.writeString(output.resolve("transport.txt"),client.previewStatistics()+"\nActual client connection refreshed an already-mounted aluminium block placeholder. Disconnect restored explicit failure.\n");
        }finally{fx(()->{if(browser.get()!=null)browser.get().deactivate();if(stage.get()!=null)stage.get().close();});InventoryPreviewProvider.shared().client(null);}
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("metrics.json").toFile(),rows);
    }
}
