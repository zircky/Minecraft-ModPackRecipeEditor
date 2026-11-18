package dev.recipeeditor.forge;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
/** Explicit test mode only; excluded from the distributable JAR. No user instance/world is touched. */
@Mod.EventBusSubscriber(modid="mre_companion",value=Dist.CLIENT)
public final class ClientIntegrationHooks {
    private static boolean ready,reloadRequested;
    @SubscribeEvent public static void tick(final TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("mre.clientProbe"))return;
        final var minecraft=Minecraft.getInstance();minecraft.options.pauseOnLostFocus=false;
        if(minecraft.player!=null&&minecraft.hasSingleplayerServer()&&!ready)try{Files.writeString(Path.of("runtime-ready.flag"),System.getProperty("mre.probeRunId","ready"));ready=true;}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        if(ready&&!reloadRequested&&Files.isRegularFile(Path.of("runtime-reload.request"))) {
            reloadRequested=true;final var server=minecraft.getSingleplayerServer();
            server.execute(()->server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((result,error)->{try{Files.writeString(Path.of("runtime-reload.done"),error==null?"OK":"ERROR");}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}));
        }
        if(ready&&Files.isRegularFile(Path.of("runtime-probe.ok")))minecraft.stop();
    }
}
