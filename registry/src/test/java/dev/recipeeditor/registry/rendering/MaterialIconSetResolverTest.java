package dev.recipeeditor.registry.rendering;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class MaterialIconSetResolverTest {
@Test void followsCapturedIconSetParentChainRatherThanInventingDefault(){final var result=new GregTechMaterialPreview(MaterialPreviewFixtures.index(false,true,"stone")).resolve(MaterialPreviewFixtures.ITEM);assertTrue(result.identity().contains("item/material_sets/dull/ingot"));}
@Test void refusesStaleSnapshotWithoutActualColors(){assertThrows(BlockModelException.class,()->new GregTechMaterialPreview(MaterialPreviewFixtures.index(false,false,"stone")).resolve(MaterialPreviewFixtures.ITEM));}
}
