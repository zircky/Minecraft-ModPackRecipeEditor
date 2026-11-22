package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class InventoryModelTexturesTest {
    @TempDir Path root;
    @Test void explicitTintIsAppliedOnlyToMatchingIndexAndNeverPollutesUntintedTexture()throws Exception{
        final String internal="assets/test/textures/block/leaf.png";
        final Path file=root.resolve(internal);Files.createDirectories(file.getParent());
        final var input=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<16;y++)for(int x=0;x<16;x++)input.setRGB(x,y,0xffffffff);ImageIO.write(input,"PNG",file.toFile());
        final var source=new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),root,internal,"fixture");
        final var id=ResourceLocation.parse("test:leaf");final var texture=ResourceLocation.parse("test:block/leaf");
        final var faces=Map.of(Direction.SOUTH,new Face(texture,source,new UV(0,0,16,16),0,null,0),Direction.NORTH,new Face(texture,source,new UV(0,0,16,16),0,null,-1));
        final var geometry=new BlockGeometryModel(id,List.of(new Element(new BlockGeometryModel.Vector(0,0,0),new BlockGeometryModel.Vector(16,16,16),faces,null,true)),List.of(source));
        final var model=new InventoryItemModel(id,InventoryItemModel.Kind.GEOMETRY,geometry,null,GuiDisplayTransform.identity(),"side",List.of(source));
        final var entry=new RegistryEntry(id,ResourceKind.ITEM,"Leaf","",Map.of(),List.of(),List.of(),DiscoveryConfidence.INFERRED_FROM_RESOURCES,"fixture",ResourceVisual.legacy(List.of()),Map.of("previewSprites","[{\"sprite\":\"test:block/leaf\",\"tintIndex\":0,\"tint\":\"ff20aa40\"}]"));
        try(final var frames=new TextureResourceService()){
            final var known=new InventoryModelTextures(frames).load(model,entry,root);
            assertFalse(known.tintUnknown());assertEquals(0xffffffff,pixel(known.images().get(source)));assertEquals(0xff20aa40,pixel(known.tinted().get("test:block/leaf#0")));assertFalse(known.tinted().containsKey("test:block/leaf#-1"));
            final var unknown=new InventoryModelTextures(frames).load(model,null,root);assertTrue(unknown.tintUnknown());assertTrue(unknown.tinted().isEmpty());
        }
    }
    private int pixel(final byte[] bytes)throws IOException{return ImageIO.read(new ByteArrayInputStream(bytes)).getRGB(0,0);}
}
