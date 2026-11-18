package dev.recipeeditor.forge;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
@GameTestHolder("mre_companion")
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
    @GameTest(templateNamespace="mre_companion",template="empty",timeoutTicks=60000)
    public static void realDesktopSynchronization(final GameTestHelper helper) {
        final var marker=Path.of("runtime-probe.ok");
        if(Files.exists(marker))throw new GameTestAssertException("Delete stale test marker before running the live test");
        try{Files.writeString(Path.of("runtime-ready.flag"),System.getProperty("mre.probeRunId","ready"));}catch(java.io.IOException failure){throw new GameTestAssertException(failure.getMessage());}
        final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(300);
        helper.onEachTick(()->{
            if(Files.isRegularFile(marker)){helper.succeed();return;}
            if(System.nanoTime()>deadline)helper.fail("Desktop integration probe did not complete within 300 seconds");
            // GameTestServer accelerates ticks. Test-only pacing lets a separate Java 21 process connect.
            try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();helper.fail("Test interrupted");}
        });
    }
}
