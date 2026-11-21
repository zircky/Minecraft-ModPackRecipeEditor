package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class GregTechMaterialCollectorTest {
    @TempDir Path root;
    @Test void unregisteredFormsCannotEnterMaterialSnapshot() {
        final var base=new RuntimeFixtures().snapshot(root,"s",1);
        final var form=new RuntimeData.MaterialForm("gtceu:ingot",RuntimeData.Kind.ITEM,"gtceu:nonexistent_ingot");
        final var material=new RuntimeData.Material("gtceu:test","Test","material.gtceu.test",Map.of(),Map.of(),List.of(),List.of(form),Map.of());
        assertThrows(IllegalArgumentException.class,()->new RuntimeData.Snapshot(2,"bad",true,base.environment(),base.registries(),base.tags(),base.recipes(),base.mods(),List.of(),List.of(material)));
    }
    @Test void separateMaterialDatasetSurvivesSaveReopenAndMerge() throws Exception {
        final var base=new RuntimeFixtures().snapshot(root,"s",1);
        final var form=new RuntimeData.MaterialForm("gtceu:ingot",RuntimeData.Kind.ITEM,"minecraft:iron_ingot");
        final var material=new RuntimeData.Material("gtceu:iron","Iron","material.gtceu.iron",Map.of("en_us","Iron"),Map.of("ingot","IngotProperty"),List.of("GENERATE_PLATE"),List.of(form),Map.of("adapter","fixture"));
        final var snapshot=new RuntimeData.Snapshot(2,"materials",true,base.environment(),base.registries(),base.tags(),base.recipes(),base.mods(),List.of(),List.of(material));
        final var path=root.resolve("materials.mresnapshot");final var store=new RuntimeSnapshotService();store.save(path,snapshot);assertEquals(snapshot,store.load(path));
        final var merged=new RegistryMergeService().merge(new dev.recipeeditor.registry.RegistryIndex(List.of()),store.load(path),path);
        assertEquals(1,merged.preferred().materials().size());assertEquals(1,merged.preferred().entries().size());
        assertEquals("minecraft:iron_ingot",merged.preferred().materials().getFirst().forms().getFirst().resourceId().toString());
    }
    @Test @Tag("external") @EnabledIfSystemProperty(named="resourceRuntimeSnapshot",matches=".+")
    void verifyMaterialCollectorAgainstActualIsgClient() throws Exception {
        try(final var client=new CompanionConnectionService()) {
            client.connect(Path.of(System.getProperty("runtimeSessionFile"))).get(15,TimeUnit.SECONDS);
            final var snapshot=client.synchronize(message->{try{Files.writeString(Path.of(System.getProperty("resourceRuntimeSnapshot")+".progress.txt"),message);}catch(java.io.IOException ignored){}}).get(320,TimeUnit.SECONDS);
            new RuntimeSnapshotService().save(Path.of(System.getProperty("resourceRuntimeSnapshot")),snapshot);
            assertFalse(snapshot.materials().isEmpty(),snapshot.diagnostics().toString());
            final var iron=snapshot.materials().stream().filter(m->m.id().equals("gtceu:iron")).findFirst().orElseThrow();
            assertFalse(iron.properties().isEmpty());assertTrue(iron.forms().stream().anyMatch(f->f.kind()==RuntimeData.Kind.ITEM&&f.resourceId().equals("minecraft:iron_ingot")),iron.forms().toString());
            assertTrue(iron.forms().stream().anyMatch(f->f.kind()==RuntimeData.Kind.FLUID));
            final var registered=new HashSet<String>();snapshot.registries().forEach(r->registered.add(r.kind()+":"+r.id()));
            for(final var material:snapshot.materials())for(final var form:material.forms())assertTrue(registered.contains(form.kind()+":"+form.resourceId()));
            final var propane=snapshot.registries().stream().filter(r->r.kind()==RuntimeData.Kind.FLUID&&r.id().equals("gtceu:propane")).findFirst().orElseThrow();
            assertTrue(propane.metadata().containsKey("stillTexture"),propane.metadata().toString());assertTrue(propane.metadata().containsKey("tintColor"));
            assertTrue(propane.metadata().containsKey("localizationKey"));
            final var dynamic=snapshot.registries().stream().filter(r->r.kind()==RuntimeData.Kind.ITEM&&r.id().equals("gtceu:aluminium_ingot")).findFirst().orElseThrow();
            assertTrue(dynamic.metadata().containsKey("previewSprites"),dynamic.metadata().toString());
            Files.writeString(Path.of(System.getProperty("resourceRuntimeSnapshot")+".result.txt"),"Actual ISG runtime: "+snapshot.environment().minecraftVersion()+" Forge "+snapshot.environment().loaderVersion()+"\nMaterials="+snapshot.materials().size()+"\nForms="+snapshot.materials().stream().mapToInt(m->m.forms().size()).sum()+"\nPropane="+propane.metadata()+"\nAluminium="+dynamic.metadata()+"\nIron="+iron+"\nDiagnostics="+snapshot.diagnostics());
        }
    }
}
