package dev.recipeeditor.ui;

import dev.recipeeditor.ui.rendering.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class BlockTextureMappingTest {
    @Test void everyFaceHasItsOwnMaterialAndUvRotationChangesCoordinates()throws Exception{
        FxLayoutTestSupport.run(()->{
            final var model=BlockRenderFixtures.cube(16,0,null);final var view=new BlockPreview3DView(model,BlockRenderFixtures.textures(model));
            assertEquals(6,view.meshes().size());assertEquals(6,view.meshes().stream().map(m->((javafx.scene.paint.PhongMaterial)m.getMaterial()).getDiffuseMap()).distinct().count());
            final var rotated=new BlockMeshBuilder().build(BlockRenderFixtures.cube(16,90,null)).getFirst().mesh();
            assertArrayEquals(new float[]{0,1,1,1,1,0,0,0},rotated.getTexCoords().toArray(null));
            final var points=rotated.getPoints();assertTrue(points.size()>0);
        });
    }
}
