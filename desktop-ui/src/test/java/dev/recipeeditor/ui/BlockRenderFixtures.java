package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.*;
import java.nio.file.Path;
import java.util.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

final class BlockRenderFixtures {
    static BlockGeometryModel cube(final double height,final int faceRotation,final Rotation rotation){
        final var faces=new EnumMap<Direction,Face>(Direction.class);
        for(final var direction:Direction.values()){final var texture=ResourceLocation.parse("test:block/"+direction.name().toLowerCase(Locale.ROOT));faces.put(direction,new Face(texture,new ResourceSource(SourceType.MOD_JAR,List.of(),Path.of("fixture.jar"),"assets/test/textures/block/"+direction+".png","test fixture"),new UV(0,0,16,16),faceRotation,direction,-1));}
        return new BlockGeometryModel(ResourceLocation.parse("test:cube"),List.of(new Element(new Vector(0,0,0),new Vector(16,height,16),faces,rotation,true)),List.of());
    }
    static Map<ResourceSource,byte[]> textures(final BlockGeometryModel model){
        final var result=new HashMap<ResourceSource,byte[]>();int color=0;
        for(final var face:model.elements().getFirst().faces().values())try{
            final var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);final int rgb=0xff000000|new int[]{0xff4444,0x44ff44,0x4444ff,0xffff44,0xff44ff,0x44ffff}[color++];
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,(x<4&&y<4)?0xff000000:rgb);
            final var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);result.put(face.source(),bytes.toByteArray());
        }catch(IOException error){throw new UncheckedIOException(error);}return result;
    }
}
