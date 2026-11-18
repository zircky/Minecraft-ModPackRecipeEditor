package dev.recipeeditor.companionclient;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class RegistryMergeServiceTest {
    @TempDir Path root;
    private RegistryEntry entry(String id){return new RegistryEntry(ResourceLocation.parse(id),ResourceKind.ITEM,"Static name","",Map.of(),List.of(new ResourceSource(SourceType.MOD_JAR,List.of("owner"),root.resolve("mod.jar"),"assets/test/model.json","test")),List.of(),DiscoveryConfidence.INFERRED_FROM_RESOURCES,"static");}
    @Test void runtimeWinsMatchingIdButStaticOriginalIsRetained(){final var staticEntry=entry("minecraft:iron_ingot");final var original=new RegistryIndex(List.of(staticEntry,entry("test:static_only")));final var result=new RegistryMergeService().merge(original,new RuntimeFixtures().snapshot(root,"s",1),root.resolve("snapshot"));assertSame(original,result.staticOriginals());assertEquals(2,result.preferred().entries().size());assertEquals(DiscoveryConfidence.CONFIRMED_BY_RUNTIME,result.preferred().find(staticEntry.id()).getFirst().confidence());assertEquals(DiscoveryConfidence.INFERRED_FROM_RESOURCES,result.preferred().find(ResourceLocation.parse("test:static_only")).getFirst().confidence());assertFalse(result.diagnostics().isEmpty());assertEquals(2,result.runtimeTags().definitions().size());assertTrue(result.runtimeTags().members(ResourceKind.ITEM,ResourceLocation.parse("test:empty")).isEmpty());}
    @Test void runtimeOnlyAndEmptySnapshots(){final var runtime=new RuntimeFixtures().snapshot(root,"s",1);assertEquals(1,new RegistryMergeService().merge(new RegistryIndex(List.of()),runtime,root.resolve("snapshot")).preferred().entries().size());final var empty=new RuntimeData.Snapshot(1,"empty",true,runtime.environment(),List.of(),List.of(),List.of(),List.of(),List.of());final var staticIndex=new RegistryIndex(List.of(entry("test:static")));assertEquals(staticIndex.entries(),new RegistryMergeService().merge(staticIndex,empty,root.resolve("snapshot")).preferred().entries());}
    @Test void incompleteSnapshotIsNeverTreatedAsAuthoritative(){final var runtime=new RuntimeFixtures().snapshot(root,"s",1);final var partial=new RuntimeData.Snapshot(1,"partial",false,runtime.environment(),runtime.registries(),runtime.tags(),runtime.recipes(),runtime.mods(),List.of());assertThrows(IllegalArgumentException.class,()->new RegistryMergeService().merge(new RegistryIndex(List.of()),partial,root.resolve("snapshot")));}
}
