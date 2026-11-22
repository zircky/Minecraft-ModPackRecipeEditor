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
    @Test void mergeRetainsTranslationsAndUnknownRuntimeFluidHasNoInventedTextureOrOwner() {
        final var base=new RuntimeFixtures().snapshot(root,"s",1);
        final var localized=new RegistryEntry(ResourceLocation.parse("minecraft:iron_ingot"),ResourceKind.ITEM,"Iron Ingot","item.minecraft.iron_ingot",Map.of("en_us","Iron Ingot","ru_ru","Железный слиток"),entry("minecraft:iron_ingot").sources(),List.of(),DiscoveryConfidence.INFERRED_FROM_RESOURCES,"static");
        final var fluid=new RuntimeData.Registry(RuntimeData.Kind.FLUID,"gtceu:propane","Propane","test.Fluid",Map.of(),Map.of());
        final var snapshot=new RuntimeData.Snapshot(1,"runtime",true,base.environment(),List.of(base.registries().getFirst(),fluid),List.of(),List.of(),List.of(),List.of());
        final var merged=new RegistryMergeService().merge(new RegistryIndex(List.of(localized)),snapshot,root.resolve("snapshot")).preferred();
        assertEquals(localized.localizedNames(),merged.find(localized.id()).getFirst().localizedNames());
        final var actualFluid=merged.find(ResourceLocation.parse("gtceu:propane")).getFirst();assertEquals(ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED,actualFluid.visual().status());assertTrue(actualFluid.sources().getFirst().modIds().isEmpty());
    }
    @Test void materialRuntimeNamesRequireAnExplicitUnambiguousClientLanguage() {
        final var base=new RuntimeFixtures().snapshot(root,"s",1);
        final var ru=new RuntimeData.Registry(RuntimeData.Kind.ITEM,"test:a","A","test.Item",Map.of("runtimeNameLanguage","ru_ru"),Map.of());
        final var en=new RuntimeData.Registry(RuntimeData.Kind.ITEM,"test:b","B","test.Item",Map.of("runtimeNameLanguage","en_us"),Map.of());
        final var material=new RuntimeData.Material("test:metal","Металл","material.test.metal",Map.of(),Map.of(),List.of(),List.of(),Map.of("nameComponent","{\"translate\":\"material.test.metal\"}"));
        for(final boolean mixed:List.of(false,true)) {
            final var snapshot=new RuntimeData.Snapshot(2,"materials",true,base.environment(),mixed?List.of(ru,en):List.of(ru),List.of(),List.of(),List.of(),List.of(),List.of(material));
            final var names=new RegistryMergeService().merge(new RegistryIndex(List.of()),snapshot,root.resolve("snapshot")).preferred().materials().getFirst().localizedNames();
            if(mixed)assertTrue(names.isEmpty());else assertEquals("Металл",names.get("ru_ru"));
        }
    }
    @Test void explicitBlockLinksAndStatePropertiesSurviveSnapshotRoundTrip()throws Exception {
        final var base=new RuntimeFixtures().snapshot(root,"s",1);
        final var item=new RuntimeData.Registry(RuntimeData.Kind.ITEM,"test:placing_item","Item","test.BlockItem",Map.of("blockItem","true","blockId","other:block"),Map.of());
        final var block=new RuntimeData.Registry(RuntimeData.Kind.BLOCK,"other:block","Block","test.Block",Map.of("hasBlockItem","true"),Map.of("facing",List.of("north","south")));
        final var snapshot=new RuntimeData.Snapshot(1,"links",true,base.environment(),List.of(item,block),List.of(),List.of(),List.of(),List.of());
        final var path=root.resolve("links.mresnapshot");final var service=new RuntimeSnapshotService();service.save(path,snapshot);
        final var index=new RegistryMergeService().merge(new RegistryIndex(List.of()),service.load(path),path).preferred();
        assertEquals(ResourceLocation.parse("other:block"),new BlockItemRelationships(index).block(ResourceLocation.parse(item.id())).orElseThrow());
        final var metadata=index.entries(ResourceKind.BLOCK).getFirst().metadata();assertEquals("test.Block",metadata.get("runtimeClass"));assertEquals("{\"facing\":[\"north\",\"south\"]}",metadata.get("blockStateProperties"));
    }
}
