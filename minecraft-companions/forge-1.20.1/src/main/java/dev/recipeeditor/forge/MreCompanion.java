package dev.recipeeditor.forge;

import dev.recipeeditor.companionserver.ReadOnlyCompanionServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.*;
import net.minecraftforge.event.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

@Mod("mre_companion")
public final class MreCompanion {
    private final Logger logger=LoggerFactory.getLogger(MreCompanion.class);
    private final ForgeSnapshotProvider provider=new ForgeSnapshotProvider();
    private ReadOnlyCompanionServer transport;
    public MreCompanion() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::stopped);MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::reload);
    }
    private void setup(final FMLCommonSetupEvent event) {event.enqueueWork(()->{try{transport=new ReadOnlyCompanionServer(provider,RuntimeItemPreviews.bridge());transport.credentials().save(FMLPaths.CONFIGDIR.get().resolve("mre_companion/session.json"));transport.start();logger.info("MRE Companion read-only listener ready on 127.0.0.1:{}; credentials stored in config/mre_companion/session.json",transport.credentials().port());Runtime.getRuntime().addShutdownHook(new Thread(()->transport.close(),"mre-shutdown"));}catch(IOException|RuntimeException failure){if(transport!=null)transport.close();logger.error("Companion transport unavailable; gameplay remains unchanged",failure);}});}
    private void started(final ServerStartedEvent event){provider.start(event.getServer());}
    private void stopped(final ServerStoppedEvent event){provider.stop();}
    private void tick(final TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)provider.tick();}
    private void reload(final OnDatapackSyncEvent event){if(event.getPlayer()==null)provider.invalidate();}
}
