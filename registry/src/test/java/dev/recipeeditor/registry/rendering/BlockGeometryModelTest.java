package dev.recipeeditor.registry.rendering;

import java.util.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.*;

final class BlockGeometryModelTest {
    @Test void rejectsNonfiniteGeometryAndInvalidFaceRotation(){
        assertThrows(BlockModelException.class,()->new Vector(Double.NaN,0,0));assertThrows(BlockModelException.class,()->new UV(0,0,Double.POSITIVE_INFINITY,16));
        assertThrows(BlockModelException.class,()->new Rotation(new Vector(8,8,8),Axis.X,90,false));
        assertDoesNotThrow(()->new Rotation(new Vector(8,8,8),Axis.X,-22.5,true));
    }
    @Test void emptyGeometryIsNotAcceptedAsASuccessfulPreview(){assertThrows(BlockModelException.class,()->new BlockGeometryModel(dev.recipeeditor.core.ResourceLocation.parse("test:empty"),List.of(),List.of()));}
}
