package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.registry.rendering.BlockGeometryModel;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.*;
import java.util.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import javafx.application.Platform;
import javafx.scene.shape.TriangleMesh;

/** Per-face batches are a material boundary; future atlas batching can reuse the same UVs. */
public final class BlockMeshBuilder {
    public record Part(TriangleMesh mesh,Face face,boolean shade) { }
    public List<Part> build(final BlockGeometryModel model){
        if(!Platform.isFxApplicationThread())throw new IllegalStateException("Build scene resources on the FX thread");
        final var parts=new ArrayList<Part>();
        for(final var element:model.elements())for(final var entry:element.faces().entrySet()){
            final var face=entry.getValue();final var mesh=new TriangleMesh();
            for(final var point:corners(entry.getKey(),element.from(),element.to())){
                final var rotated=rotate(point,element.rotation());mesh.getPoints().addAll((float)(rotated.x()-8),(float)(8-rotated.y()),(float)(8-rotated.z()));
            }
            final var uv=face.uv();final double[][] coordinates={{uv.u0(),uv.v0()},{uv.u0(),uv.v1()},{uv.u1(),uv.v1()},{uv.u1(),uv.v0()}};
            for(int i=0;i<4;i++){final var value=coordinates[(i+face.rotation()/90)%4];mesh.getTexCoords().addAll((float)(value[0]/16),(float)(value[1]/16));}
            mesh.getFaces().addAll(0,0,1,1,2,2,0,0,2,2,3,3);mesh.getFaceSmoothingGroups().addAll(0,0);
            parts.add(new Part(mesh,face,element.shade()));
        }
        return List.copyOf(parts);
    }
    private List<Vector> corners(final Direction direction,final Vector a,final Vector b){
        final double x=a.x(),y=a.y(),z=a.z(),X=b.x(),Y=b.y(),Z=b.z();
        return switch(direction){
            case SOUTH->List.of(new Vector(x,Y,Z),new Vector(x,y,Z),new Vector(X,y,Z),new Vector(X,Y,Z));
            case NORTH->List.of(new Vector(X,Y,z),new Vector(X,y,z),new Vector(x,y,z),new Vector(x,Y,z));
            case EAST->List.of(new Vector(X,Y,Z),new Vector(X,y,Z),new Vector(X,y,z),new Vector(X,Y,z));
            case WEST->List.of(new Vector(x,Y,z),new Vector(x,y,z),new Vector(x,y,Z),new Vector(x,Y,Z));
            case UP->List.of(new Vector(x,Y,z),new Vector(x,Y,Z),new Vector(X,Y,Z),new Vector(X,Y,z));
            case DOWN->List.of(new Vector(x,y,Z),new Vector(x,y,z),new Vector(X,y,z),new Vector(X,y,Z));
        };
    }
    private Vector rotate(final Vector point,final Rotation rotation){
        if(rotation==null)return point;final var origin=rotation.origin();final double[] v={point.x()-origin.x(),point.y()-origin.y(),point.z()-origin.z()};
        final int axis=rotation.axis().ordinal(),a=(axis+1)%3,b=(axis+2)%3;final double angle=Math.toRadians(rotation.angle()),cos=Math.cos(angle),sin=Math.sin(angle),u=v[a],w=v[b];
        v[a]=u*cos-w*sin;v[b]=u*sin+w*cos;if(rotation.rescale()){v[a]/=cos;v[b]/=cos;}
        return new Vector(v[0]+origin.x(),v[1]+origin.y(),v[2]+origin.z());
    }
}
