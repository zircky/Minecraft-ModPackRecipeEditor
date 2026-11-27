package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.registry.rendering.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Orthographic depth-buffered rasterization of textured OBJ faces, including on JavaFX software backends. */
final class ObjInventoryRenderer {
    private record Point(double x,double y,double z,double u,double v) {}

    StaticInventoryRenderer.Pixels render(final InventoryItemModel model,final byte[] bytes,final int size)throws IOException{
        if(bytes==null)throw new IOException("OBJ texture missing");
        final var texture=ImageIO.read(new ByteArrayInputStream(bytes));
        if(texture==null)throw new IOException("Invalid OBJ texture");
        final int[] pixels=new int[size*size];
        final double[] depth=new double[pixels.length];
        Arrays.fill(depth,Double.NEGATIVE_INFINITY);
        for(final var triangle:model.obj().triangles())
            draw(project(triangle.a(),model.gui(),size),project(triangle.b(),model.gui(),size),
                    project(triangle.c(),model.gui(),size),texture,pixels,depth,size);
        return new StaticInventoryRenderer.Pixels(size,size,pixels);
    }

    private Point project(final ObjItemMesh.Vertex vertex,final GuiDisplayTransform gui,final int size){
        final var right=rotate(new Vector(vertex.x()-.5,vertex.y()-.5,vertex.z()-.5),gui.rightRotation());
        final var scale=gui.scale();
        final var p=rotate(new Vector(right.x()*scale.x(),right.y()*scale.y(),right.z()*scale.z()),gui.rotation());
        final var shift=gui.translation();
        return new Point(size*(.5+p.x()+shift.x()),size*(.5-p.y()-shift.y()),size*(p.z()+shift.z()),vertex.u(),vertex.v());
    }

    private Vector rotate(final Vector p,final Vector rotation){
        final double rx=Math.toRadians(rotation.x()),ry=Math.toRadians(rotation.y()),rz=Math.toRadians(rotation.z());
        final double x=p.x()*Math.cos(rz)-p.y()*Math.sin(rz),y=p.x()*Math.sin(rz)+p.y()*Math.cos(rz);
        final double u=x*Math.cos(ry)+p.z()*Math.sin(ry),z=-x*Math.sin(ry)+p.z()*Math.cos(ry);
        return new Vector(u,y*Math.cos(rx)-z*Math.sin(rx),y*Math.sin(rx)+z*Math.cos(rx));
    }

    private void draw(final Point a,final Point b,final Point c,final BufferedImage source,final int[] pixels,final double[] depth,final int size){
        final double area=edge(a,b,c.x(),c.y());
        if(Math.abs(area)<1e-8)return;
        final double nx=(b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
        final double ny=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
        final double nz=-area;
        final double shade=.62+.38*Math.min(1,Math.abs(.25*nx-.65*ny+.7*nz)/Math.sqrt(nx*nx+ny*ny+nz*nz));
        final int left=Math.max(0,(int)Math.floor(Math.min(a.x(),Math.min(b.x(),c.x()))));
        final int right=Math.min(size-1,(int)Math.ceil(Math.max(a.x(),Math.max(b.x(),c.x()))));
        final int top=Math.max(0,(int)Math.floor(Math.min(a.y(),Math.min(b.y(),c.y()))));
        final int bottom=Math.min(size-1,(int)Math.ceil(Math.max(a.y(),Math.max(b.y(),c.y()))));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            final double u=edge(b,c,x+.5,y+.5)/area,v=edge(c,a,x+.5,y+.5)/area,w=1-u-v;
            if(u<0||v<0||w<0)continue;
            final double z=u*a.z()+v*b.z()+w*c.z();
            final int offset=y*size+x;
            if(z<=depth[offset])continue;
            final int sx=sample(u*a.u()+v*b.u()+w*c.u(),source.getWidth());
            final int sy=sample(u*a.v()+v*b.v()+w*c.v(),source.getHeight());
            final int argb=source.getRGB(sx,sy);
            if((argb>>>24)==0)continue;
            pixels[offset]=(argb&0xff000000)|((int)(((argb>>>16)&255)*shade)<<16)|((int)(((argb>>>8)&255)*shade)<<8)|(int)((argb&255)*shade);
            depth[offset]=z;
        }
    }

    private double edge(final Point a,final Point b,final double x,final double y){return (b.x()-a.x())*(y-a.y())-(b.y()-a.y())*(x-a.x());}
    private int sample(final double value,final int size){return Math.max(0,Math.min(size-1,(int)(value*size)));}
}
