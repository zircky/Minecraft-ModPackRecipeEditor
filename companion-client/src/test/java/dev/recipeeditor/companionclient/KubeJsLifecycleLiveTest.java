package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
final class KubeJsLifecycleLiveTest {
    @Test @EnabledIfSystemProperty(named="kubeJsLifecycleStage",matches="create|update|delete")
    void compareActualRecipeManagerAfterExportAndReload()throws Exception{
        final String stage=System.getProperty("kubeJsLifecycleStage");final var directory=Files.createDirectories(Path.of(System.getProperty("kubeJsLifecycleDirectory")));
        try(final var client=new CompanionConnectionService()){
            client.connect(Path.of(System.getProperty("runtimeSessionFile"))).get(10,TimeUnit.SECONDS);
            final var snapshot=client.synchronize(p->{}).get(130,TimeUnit.SECONDS);assertTrue(snapshot.complete());
            assertTrue(snapshot.mods().stream().anyMatch(m->m.id().equals("kubejs")&&m.version().equals("2001.6.5-build.26")));
            final var custom=snapshot.recipes().stream().filter(r->r.id().equals("mre_export_test:vanilla_lifecycle")).toList();
            final var planks=snapshot.recipes().stream().filter(r->r.id().equals("biomesoplenty:jacaranda_planks")).toList();
            if(!stage.equals("delete")){assertEquals(1,custom.size());assertEquals(2,custom.getFirst().properties().getAsJsonObject("result").get("count").getAsInt());}
            if(!stage.equals("create")){assertEquals(1,planks.size());assertEquals(5,planks.getFirst().properties().getAsJsonObject("result").get("count").getAsInt());}
            if(stage.equals("delete"))assertTrue(snapshot.recipes().stream().noneMatch(r->r.id().equals("minecraft:tnt")),"Exact-ID TNT removal was not applied");
            new RuntimeSnapshotService().save(directory.resolve(stage+".mresnapshot"),snapshot);
            Files.writeString(directory.resolve(stage+"-result.txt"),"Actual Minecraft "+snapshot.environment().minecraftVersion()+" Forge "+snapshot.environment().loaderVersion()+" KubeJS 2001.6.5-build.26\nStage="+stage+"\nRecipes="+snapshot.recipes().size()+"\nAssertions passed against live Server RecipeManager\n"+(stage.equals("delete")?"minecraft:tnt absent":custom.getFirst().properties())+"\nPlanks="+(planks.isEmpty()?"absent":planks.getFirst().properties()));
        }
    }
}
