package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.ResourceSource;
import java.util.*;

/** Minecraft coordinates and resolved per-face assets; no scene graph or JavaFX dependency. */
public record BlockGeometryModel(ResourceLocation id,List<Element> elements,List<ResourceSource> sources) {
    public BlockGeometryModel { Objects.requireNonNull(id);elements=List.copyOf(elements);sources=List.copyOf(sources);if(elements.isEmpty()||elements.size()>256)throw new BlockModelException(BlockModelException.Code.INVALID_GEOMETRY,"Expected 1..256 elements"); }
    public enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
    public enum Axis { X,Y,Z }
    public record Vector(double x,double y,double z) {
        public Vector {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new BlockModelException(BlockModelException.Code.INVALID_GEOMETRY,"Nonfinite coordinate");}
    }
    public record UV(double u0,double v0,double u1,double v1) {
        public UV {for(final double value:new double[]{u0,v0,u1,v1})if(!Double.isFinite(value)||value<0||value>16)throw new BlockModelException(BlockModelException.Code.INVALID_UV,"UV outside 0..16 is not supported");}
    }
    public record Rotation(Vector origin,Axis axis,double angle,boolean rescale) {
        public Rotation {Objects.requireNonNull(origin);Objects.requireNonNull(axis);if(Math.abs(origin.x())>1024||Math.abs(origin.y())>1024||Math.abs(origin.z())>1024)throw new BlockModelException(BlockModelException.Code.INVALID_GEOMETRY,"Rotation origin exceeds preview limits");if(!Set.of(-45.0,-22.5,0.0,22.5,45.0).contains(angle))throw new BlockModelException(BlockModelException.Code.INVALID_GEOMETRY,"Invalid element rotation");}
    }
    public record Face(ResourceLocation texture,ResourceSource source,UV uv,int rotation,Direction cullface,int tintIndex) {
        public Face {Objects.requireNonNull(texture);Objects.requireNonNull(source);Objects.requireNonNull(uv);if(rotation<0||rotation>270||rotation%90!=0)throw new BlockModelException(BlockModelException.Code.INVALID_UV,"Face rotation must be 0/90/180/270");}
    }
    public record Element(Vector from,Vector to,Map<Direction,Face> faces,Rotation rotation,boolean shade) {
        public Element {
            Objects.requireNonNull(from);Objects.requireNonNull(to);faces=Map.copyOf(faces);
            if(faces.isEmpty()||faces.size()>6)throw new BlockModelException(BlockModelException.Code.INVALID_GEOMETRY,"Expected 1..6 faces");
            final double[] a={from.x(),from.y(),from.z()},b={to.x(),to.y(),to.z()};int extent=0;
            for(int i=0;i<3;i++){if(a[i]<-16||b[i]>32||a[i]>b[i])throw new BlockModelException(BlockModelException.Code.INVALID_GEOMETRY,"Invalid element bounds");if(b[i]>a[i])extent++;}
            if(extent<2)throw new BlockModelException(BlockModelException.Code.INVALID_GEOMETRY,"Degenerate element");
        }
    }
}
