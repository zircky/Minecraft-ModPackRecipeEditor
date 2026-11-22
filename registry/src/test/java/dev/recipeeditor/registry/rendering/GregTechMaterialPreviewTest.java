package dev.recipeeditor.registry.rendering;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GregTechMaterialPreviewTest {
@Test void preservesActualFormLayersAndIndependentTintColors(){final var result=new GregTechMaterialPreview(MaterialPreviewFixtures.index(false,true,"stone")).resolve(MaterialPreviewFixtures.ITEM);assertEquals(InventoryItemModel.Kind.GENERATED_2D,result.model().kind());final var layers=result.textures().values().iterator().next().layers();assertEquals(2,layers.size());assertEquals(0xff20aa40,layers.get(0).argbTint());assertEquals(0xffffffff,layers.get(1).argbTint());assertNotEquals(layers.get(0).source(),layers.get(1).source());}
}
