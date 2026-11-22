package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BlockRegistryNormalizationTest {
    @Test void doorPartsAndItemVariantsRemainAssetsRatherThanRegisteredIds(){
        final var documents=List.of(ResourceFixtures.document("assets/test/models/block/door_bottom_left.json","{}"),ResourceFixtures.document("assets/test/models/block/door_top_right.json","{}"),ResourceFixtures.document("assets/test/models/item/variant.json","{}"),ResourceFixtures.document("assets/test/blockstates/door.json","{\"variants\":{}}"));
        final var scan=new ResourceScanResult(documents,List.of(),List.of());final var diagnostics=new ArrayList<dev.recipeeditor.core.importing.ImportDiagnostic>();
        final var blocks=new BlockResourceIndexer().index(scan,new LocalizationService(documents),new TagIndex(documents),"en_us",diagnostics);
        final var items=new ItemResourceIndexer().index(scan,new LocalizationService(documents),new TagIndex(documents),"en_us",diagnostics);
        assertEquals(List.of(ResourceLocation.parse("test:door")),blocks.stream().map(RegistryEntry::id).toList());assertTrue(items.isEmpty());
        assertEquals(DiscoveryConfidence.INFERRED_FROM_RESOURCES,blocks.getFirst().confidence());
        final var catalog=new RegistryIndex(blocks,scan).assetCatalog();assertEquals(4,catalog.records().size());assertEquals(2,catalog.records(ResourceAssetRecord.Kind.BLOCK_MODEL).size());assertEquals(1,catalog.blockstates(ResourceLocation.parse("test:door")).size());
        assertEquals(documents.getFirst().source(),catalog.modelDefinitions(ResourceLocation.parse("test:block/door_bottom_left")).getFirst().source());
    }
    @Test void independentRecipeReferenceCreatesOnlyReferencedItemCandidate(){
        final var scan=new ResourceScanResult(List.of(ResourceFixtures.document("assets/test/models/item/a.json","{}"),ResourceFixtures.document("assets/test/models/item/variant.json","{}")),List.of(),List.of());
        final var reference=new ResourceReference(ResourceKind.ITEM,ResourceLocation.parse("test:a"),ResourceFixtures.document("data/test/recipes/a.json","{}").source());
        final var items=new ItemResourceIndexer().index(scan,new LocalizationService(List.of()),new TagIndex(List.of()),"en_us",new ArrayList<>(),List.of(reference));
        assertEquals(1,items.size());assertEquals(reference.id(),items.getFirst().id());assertTrue(items.getFirst().sources().contains(reference.source()));assertEquals(2,items.getFirst().sources().size());
    }
    @Test void conflictingRecipeReferencesDoNotMakeAnUnambiguousItemModelAmbiguous(){
        final var scan=new ResourceScanResult(List.of(ResourceFixtures.document("assets/test/models/item/a.json","{}")),List.of(),List.of());
        final var first=new ResourceSource(SourceType.MOD_JAR,List.of(),java.nio.file.Path.of("first.jar"),"data/test/recipes/a.json","Recipe reference");
        final var second=new ResourceSource(SourceType.MOD_JAR,List.of(),java.nio.file.Path.of("second.jar"),first.internalPath(),"Recipe reference");
        final var id=ResourceLocation.parse("test:a");
        final var entries=new ItemResourceIndexer().index(scan,new LocalizationService(List.of()),new TagIndex(List.of()),"en_us",new ArrayList<>(),List.of(new ResourceReference(ResourceKind.ITEM,id,first),new ResourceReference(ResourceKind.ITEM,id,second)));
        assertNotEquals(ResourceVisual.Status.AMBIGUOUS_SOURCE,entries.getFirst().visual().status());assertEquals(3,entries.getFirst().sources().size());
    }
}
