package dev.recipeeditor.forge;

import dev.recipeeditor.companionprotocol.ProtocolCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.util.*;

/** Test-only runtime evidence and reload/exit signals, excluded from distributed JARs. */
@Mod.EventBusSubscriber(modid="mre_companion",value=Dist.CLIENT)
public final class InventoryCaptureClientProbe {
    private static boolean ready,reloading;
    @SubscribeEvent public static void tick(final TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("mre.inventoryPreviewProbe"))return;
        final var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(!ready&&mc.screen instanceof TitleScreen)try{
            mc.getWindow().setTitle("MRE Inventory Capture Verification — Minecraft 1.20.1");
            final var data=new TreeMap<String,Object>();data.put("minecraft","1.20.1");data.put("mods",ModList.get().getMods().stream().map(mod->Map.of("id",mod.getModId(),"version",mod.getVersion().toString())).toList());data.put("items",ForgeRegistries.ITEMS.getKeys().stream().map(Object::toString).sorted().toList());
            Files.writeString(Path.of("inventory-items.json"),ProtocolCodec.GSON.toJson(data));Files.writeString(Path.of("inventory-ready.flag"),"READY");ready=true;
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
        if(!ready)return;
        if(!reloading&&Files.isRegularFile(Path.of("inventory-reload.request"))){reloading=true;mc.reloadResourcePacks().whenComplete((ignored,error)->{try{Files.writeString(Path.of("inventory-reload.done"),error==null?"OK":"ERROR");}catch(java.io.IOException failed){throw new IllegalStateException(failed);}});}
        if(Files.isRegularFile(Path.of("inventory-stop.request")))mc.stop();
    }
}
