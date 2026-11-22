package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.ItemPreviewClient;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.ui.rendering.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javafx.scene.*;
import javafx.scene.control.*;

final class InventorySurfaceTestSupport {
    static final class Client implements ItemPreviewClient {
        final AtomicInteger calls=new AtomicInteger();final List<ItemPreviewRequest> requests=new CopyOnWriteArrayList<>();volatile String context=UUID.randomUUID().toString();volatile boolean available=true;
        public boolean itemPreviewsAvailable(){return available;}public String previewContext(){return context;}
        public CompletableFuture<List<ItemPreviewResponse>> itemPreviews(final List<ItemPreviewRequest> items){calls.incrementAndGet();requests.addAll(items);try{final var result=new ArrayList<ItemPreviewResponse>();for(final var request:items){final var image=new BufferedImage(request.size(),request.size(),BufferedImage.TYPE_INT_ARGB);for(int y=8;y<request.size()-8;y++)for(int x=8;x<request.size()-8;x++)image.setRGB(x,y,request.nbt().isEmpty()?0xff00ff00:0xffff0000);final var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);result.add(new ItemPreviewResponse(request,"MINECRAFT_CLIENT_INVENTORY","READY",context,Base64.getEncoder().encodeToString(out.toByteArray()),"Synthetic UI transport fixture; not a Minecraft runtime verification"));}return CompletableFuture.completedFuture(result);}catch(IOException e){return CompletableFuture.failedFuture(e);}}
    }
    static DiscoveryResult discovery(){final var base=FxLayoutTestSupport.discovery();final var entry=new RegistryEntry(ResourceLocation.parse("test:inventory_icon"),ResourceKind.ITEM,"Inventory icon","",Map.of("en_us","Inventory icon"),List.of(),List.of(),DiscoveryConfidence.INFERRED_FROM_RESOURCES,"fixture");return new DiscoveryResult(base.selectedRoot(),new RegistryIndex(List.of(entry)),base.recipes(),List.of(),0);}
    static ItemPreviewCache.Image await(final Parent root)throws Exception{
        final var found=new AtomicReference<ItemPreviewCache.Image>();final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline){FxLayoutTestSupport.run(()->{root.applyCss();root.layout();for(final var node:root.lookupAll(".inventory-preview"))if(node.getProperties().get("inventory.preview") instanceof ItemPreviewCache.Image image)found.set(image);});if(found.get()!=null)return found.get();Thread.sleep(20);}
        throw new AssertionError("No inventory icon appeared on mounted UI surface");
    }
}
