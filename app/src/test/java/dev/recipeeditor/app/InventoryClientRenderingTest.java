package dev.recipeeditor.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.companionprotocol.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Minecraft client rendering, not a mock server or a static texture test. */
@Tag("external")
final class InventoryClientRenderingTest {
    @Test @EnabledIfSystemProperty(named="inventoryReloadProbe",matches=".+")
    void actualResourcePackReloadChangesNativePreviewContext()throws Exception{
        final Path probe=Path.of(System.getProperty("inventoryReloadProbe")).toAbsolutePath().normalize();
        assertEquals("run-inventory",probe.getFileName().toString(),"Only the isolated test client accepts probe signals");
        try(final var client=new CompanionConnectionService()){
            client.connect(Path.of(System.getProperty("previewSession"))).get(8,TimeUnit.SECONDS);
            final var request=List.of(new ItemPreviewRequest("minecraft:stone",64));
            final var before=client.itemPreviews(request).get(8,TimeUnit.SECONDS).getFirst();assertEquals("READY",before.status());
            Files.writeString(probe.resolve("inventory-reload.request"),"RELOAD");
            final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
            while(!Files.exists(probe.resolve("inventory-reload.done"))&&System.nanoTime()<deadline)Thread.sleep(100);
            assertEquals("OK",Files.readString(probe.resolve("inventory-reload.done")));
            final var after=client.itemPreviews(request).get(8,TimeUnit.SECONDS).getFirst();assertEquals("READY",after.status());assertNotEquals(before.context(),after.context());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Files.createDirectories(Path.of("build/inventory-evidence")).resolve("client-reload.json").toFile(),Map.of("before",before.context(),"after",after.context(),"status","ACTUAL_CLIENT_RESOURCE_RELOAD_PASSED"));
        }
    }
    @Test @EnabledIfSystemProperty(named="previewSession",matches=".+")
    void genuineClientItemRendererCapturesDefaultStacksAndExplicitNbtVariants()throws Exception{
        final Path output=Files.createDirectories(Path.of("build/inventory-evidence"));final var rows=new ArrayList<Map<String,Object>>();
        try(final var client=new CompanionConnectionService()){
            client.connect(Path.of(System.getProperty("previewSession"))).get(8,TimeUnit.SECONDS);assertTrue(client.itemPreviewsAvailable(),"Actual client must negotiate item-previews-v1");
            final var ids=new ArrayList<String>();for(final var name:List.of("stone","oak_planks","oak_stairs","oak_slab","oak_fence","oak_door","crafting_table","furnace","diamond","iron_ingot"))ids.add("minecraft:"+name);
            final String registry=System.getProperty("inventoryClientRegistry");
            if(registry!=null){final var data=new ObjectMapper().readTree(Path.of(registry).toFile());for(final String namespace:List.of("gtceu","ad_astra","botania","create","mekanism","nuclearcraft")){int count=0;for(final var item:data.path("items"))if(item.asText().startsWith(namespace+":")&&(item.asText().contains("casing")||item.asText().contains("planks")||item.asText().contains("machine"))&&count++<3)ids.add(item.asText());}}
            if(registry!=null){final var registered=new HashSet<String>();new ObjectMapper().readTree(Path.of(registry).toFile()).path("items").forEach(item->registered.add(item.asText()));for(final var custom:List.of("gtceu:lv_macerator","gtceu:aluminium_fluid_cell"))if(registered.contains(custom)&&!ids.contains(custom))ids.add(custom);}
            final var sheet=new BufferedImage(5*180,((ids.size()+4)/5)*110,BufferedImage.TYPE_INT_ARGB);final var graphics=sheet.createGraphics();graphics.setColor(new java.awt.Color(32,33,36));graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            for(int offset=0;offset<ids.size();offset+=4){final var requests=ids.subList(offset,Math.min(offset+4,ids.size())).stream().map(id->new ItemPreviewRequest(id,64)).toList();final var response=client.itemPreviews(requests).get(8,TimeUnit.SECONDS);
                for(int i=0;i<response.size();i++){final var row=response.get(i);assertEquals("READY",row.status(),row.request().resourceId()+": "+row.diagnostic());assertEquals("MINECRAFT_CLIENT_INVENTORY",row.source());final var image=ImageIO.read(new ByteArrayInputStream(row.png()));assertNotNull(image);assertEquals(0,image.getRGB(0,0)>>>24);long pixels=0;for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((image.getRGB(x,y)>>>24)>0)pixels++;assertTrue(pixels>20,row.request().resourceId()+" is blank");
                    final String name=row.request().resourceId().replace(':','_').replace('/','_');Files.write(output.resolve("client-"+name+".png"),row.png());final int position=offset+i;graphics.drawImage(image,(position%5)*180+58,(position/5)*110+5,null);graphics.setColor(java.awt.Color.WHITE);graphics.drawString(row.request().resourceId().substring(row.request().resourceId().indexOf(':')+1),(position%5)*180+4,(position/5)*110+90);
                    rows.add(Map.of("id",row.request().resourceId(),"source",row.source(),"context",row.context(),"status",row.status(),"nontransparent_pixels",pixels,"diagnostic",row.diagnostic(),"png_bytes",row.png().length));
                }
            }
            final var nbt=client.itemPreviews(List.of(new ItemPreviewRequest("minecraft:potion",64,"nbt","{Potion:\"minecraft:healing\"}"),new ItemPreviewRequest("minecraft:potion",64,"nbt","{Potion:\"minecraft:poison\"}"))).get(8,TimeUnit.SECONDS);assertEquals("READY",nbt.get(0).status());assertEquals("READY",nbt.get(1).status());assertFalse(Arrays.equals(nbt.get(0).png(),nbt.get(1).png()),"Actual ItemStack NBT must affect native item colors");Files.write(output.resolve("client-potion-healing.png"),nbt.get(0).png());Files.write(output.resolve("client-potion-poison.png"),nbt.get(1).png());
            graphics.dispose();ImageIO.write(sheet,"PNG",output.resolve("client-inventory.png").toFile());
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("client-metrics.json").toFile(),rows);
    }
}
