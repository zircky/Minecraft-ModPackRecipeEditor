package dev.recipeeditor.companionclient;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import dev.recipeeditor.companionprotocol.RuntimeData;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("external")
final class RuntimeLiveIntegrationTest {
    @TempDir Path temporary;
    @Test @EnabledIfSystemProperty(named="runtimeSessionFile",matches=".+")
    void actualForgeRegistryRecipeSnapshotAndReconnect()throws Exception {
        final var session=Path.of(System.getProperty("runtimeSessionFile"));
        final String ready=System.getProperty("runtimeReadyFlag");if(ready!=null){final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(240);while(!Files.isRegularFile(Path.of(ready))||(System.getProperty("runtimeProbeRunId")!=null&&!Files.readString(Path.of(ready)).trim().equals(System.getProperty("runtimeProbeRunId")))){if(System.nanoTime()>deadline)throw new AssertionError("Forge world readiness timeout");Thread.sleep(250);}}
        try(final var client=new CompanionConnectionService()) {
            client.connect(session).get(10,TimeUnit.SECONDS);
            final var first=client.synchronize(stage->{}).get(130,TimeUnit.SECONDS);
            assertTrue(first.complete());assertTrue(first.registries().stream().anyMatch(e->e.kind()==RuntimeData.Kind.ITEM&&e.id().equals("minecraft:stone")));
            assertTrue(first.registries().stream().anyMatch(e->e.kind()==RuntimeData.Kind.BLOCK&&e.id().equals("minecraft:stone")));
            assertTrue(first.registries().stream().anyMatch(e->e.kind()==RuntimeData.Kind.FLUID&&e.id().equals("minecraft:water")));
            assertFalse(first.tags().isEmpty());assertTrue(first.recipes().stream().anyMatch(r->r.type().equals("minecraft:smelting")));
            final var gtRecipes=first.recipes().stream().filter(r->r.adapter().equals("gtceu-7.1.4")).toList();
            if(first.mods().stream().anyMatch(m->m.id().equals("gtceu")&&m.version().equals("7.1.4"))) {
                assertFalse(gtRecipes.isEmpty(),"GT adapter did not collect any recipes");assertTrue(gtRecipes.stream().anyMatch(r->r.properties().has("duration")&&r.properties().has("inputs")&&r.properties().has("outputs")),"Missing GT codec properties");
                assertTrue(gtRecipes.stream().anyMatch(r->r.properties().has("inputEUt")),"Missing EU/t");
            }
            final var store=new RuntimeSnapshotService();final var file=temporary.resolve("actual.mresnapshot");store.save(file,first);assertEquals(first,store.load(file));Files.createDirectories(Path.of("build"));store.save(Path.of("build/actual-runtime.mresnapshot"),first);
            long generation=first.environment().generation();final String reloadRequest=System.getProperty("runtimeReloadRequest");
            if(reloadRequest!=null){Files.writeString(Path.of(reloadRequest),"Reload the isolated test world");final var complete=Path.of(System.getProperty("runtimeReloadComplete"));final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);while(!Files.exists(complete)){if(System.nanoTime()>deadline)throw new AssertionError("Runtime reload timeout");Thread.sleep(100);}assertEquals("OK",Files.readString(complete));generation=-1;}
            verifyRepeatedSync(client,first.snapshotId(),generation);
            client.disconnect();assertEquals(CompanionConnectionStatus.DISCONNECTED,client.status());client.connect(session).get(10,TimeUnit.SECONDS);assertTrue(client.synchronize(stage->{}).get(130,TimeUnit.SECONDS).complete());
            Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/runtime-live-result.txt"),"Actual Forge runtime: "+first.environment()+"\nRegistries="+first.registries().size()+", tags="+first.tags().size()+", recipes="+first.recipes().size()+", mods="+first.mods().size()+"\nGT adapter recipes="+gtRecipes.size()+"\nTCP/auth/sync/snapshot/re-sync/disconnect/reconnect verified. GTCEu present="+first.mods().stream().anyMatch(m->m.id().equals("gtceu"))+"\n");
            final String marker=System.getProperty("runtimeProbeMarker");if(marker!=null){Files.writeString(Path.of(marker),"Actual desktop integration assertions passed");if(Boolean.getBoolean("runtimeExpectServerStop")){final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(client.status()!=CompanionConnectionStatus.ERROR){if(System.nanoTime()>deadline)throw new AssertionError("Minecraft shutdown was not detected");Thread.sleep(100);}Files.writeString(Path.of("build/runtime-live-result.txt"),"Minecraft process shutdown detected by heartbeat\n",StandardOpenOption.APPEND);}}
        }
    }
    private void verifyRepeatedSync(final CompanionConnectionService client,final String previousId,final long generation)throws Exception {final var second=client.synchronize(stage->{}).get(130,TimeUnit.SECONDS);assertNotEquals(previousId,second.snapshotId());if(generation<0)assertTrue(second.environment().generation()>1,"Datapack reload did not invalidate runtime generation");else assertEquals(generation,second.environment().generation());}
}
