package dev.recipeeditor.ui;

import dev.recipeeditor.ui.rendering.BlockMeshBuilder;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class BlockMeshBuilderTest {
    @Test void cubeHasSixRealQuadsAndTwelveTrianglesWithThreeDimensionalBounds()throws Exception{
        FxLayoutTestSupport.run(()->{
            final var parts=new BlockMeshBuilder().build(BlockRenderFixtures.cube(16,0,null));assertEquals(6,parts.size());
            assertEquals(12,parts.stream().mapToInt(p->p.mesh().getFaces().size()/6).sum());
            final var group=new javafx.scene.Group();parts.forEach(p->group.getChildren().add(new javafx.scene.shape.MeshView(p.mesh())));
            final var bounds=group.getBoundsInLocal();assertEquals(16,bounds.getWidth(),0.001);assertEquals(16,bounds.getHeight(),0.001);assertEquals(16,bounds.getDepth(),0.001);
        });
    }
    @Test void slabHeightAndElementRotationAreNotReplacedWithFullCube()throws Exception{
        FxLayoutTestSupport.run(()->{
            final var builder=new BlockMeshBuilder();final var group=new javafx.scene.Group();builder.build(BlockRenderFixtures.cube(8,0,null)).forEach(p->group.getChildren().add(new javafx.scene.shape.MeshView(p.mesh())));assertEquals(8,group.getBoundsInLocal().getHeight(),0.001);
            final var origin=new Vector(8,8,8);final var plain=builder.build(BlockRenderFixtures.cube(16,0,null)).getFirst().mesh().getPoints().toArray(null);
            final var rotated=builder.build(BlockRenderFixtures.cube(16,0,new Rotation(origin,Axis.Y,45,true))).getFirst().mesh().getPoints().toArray(null);
            assertFalse(java.util.Arrays.equals(plain,rotated));assertTrue(java.util.stream.IntStream.range(0,rotated.length).allMatch(i->Float.isFinite(rotated[i])));
        });
    }
}
