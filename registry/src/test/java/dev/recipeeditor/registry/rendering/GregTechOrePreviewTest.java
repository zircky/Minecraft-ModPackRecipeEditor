package dev.recipeeditor.registry.rendering;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GregTechOrePreviewTest {
@Test void baseRockRemainsUntintedAndOnlyOverlayReceivesMaterialColor(){final var result=new GregTechMaterialPreview(MaterialPreviewFixtures.index(true,true,"netherrack")).resolve(MaterialPreviewFixtures.ITEM);assertEquals(InventoryItemModel.Kind.GEOMETRY,result.model().kind());assertEquals(6,result.model().geometry().elements().getFirst().faces().size());final var layers=result.textures().values().iterator().next().layers();assertNull(layers.get(0).argbTint());assertTrue(layers.get(0).source().internalPath().contains("netherrack"));assertEquals(0xff20aa40,layers.get(1).argbTint());}
@Test void differentRockEvidenceProducesDifferentSource(){final var a=new GregTechMaterialPreview(MaterialPreviewFixtures.index(true,true,"stone")).resolve(MaterialPreviewFixtures.ITEM);final var b=new GregTechMaterialPreview(MaterialPreviewFixtures.index(true,true,"deepslate")).resolve(MaterialPreviewFixtures.ITEM);assertNotEquals(a.textures().keySet(),b.textures().keySet());}
}
