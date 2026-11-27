package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.transform.*;

/** One reusable offscreen scene. No controls, orbit camera, per-card scene or arbitrary fit transform. */
public final class StaticInventoryRenderer {
    public record Pixels(int width,int height,int[] argb){public Pixels{argb=argb.clone();}@Override public int[] argb(){return argb.clone();}}
    private Group root;
    private SubScene scene;
    private Scene owner;
    private boolean busy;
    public CompletableFuture<Pixels> render(final InventoryItemModel model,final Map<ResourceSource,byte[]> images,final int size){
        return render(model,images,Map.of(),size);
    }
    public CompletableFuture<Pixels> render(final InventoryItemModel model,final Map<ResourceSource,byte[]> images,final Map<String,byte[]> tinted,final int size){
        if(model.kind()==InventoryItemModel.Kind.OBJ){
            try{return CompletableFuture.completedFuture(new ObjInventoryRenderer().render(model,images.get(model.obj().texture()),size));}
            catch(Exception invalid){return CompletableFuture.failedFuture(invalid);}
        }
        final var result=new CompletableFuture<Pixels>();Platform.runLater(()->{
            try{
                if(!Platform.isSupported(ConditionalFeature.SCENE3D))throw new IllegalStateException("SCENE3D unavailable");
                if(busy)throw new IllegalStateException("Only one inventory snapshot per render batch");busy=true;
                if(scene==null){root=new Group();scene=new SubScene(root,size,size,true,SceneAntialiasing.BALANCED);scene.setFill(Color.TRANSPARENT);final var camera=new ParallelCamera();camera.setNearClip(0.1);camera.setFarClip(10000);scene.setCamera(camera);owner=new Scene(new Group(scene),size,size,true);}
                scene.setWidth(size);scene.setHeight(size);final var geometry=model.kind()==InventoryItemModel.Kind.GEOMETRY?model.geometry():plane(model);
                final var decoded=new HashMap<ResourceSource,Image>();images.forEach((source,png)->{final var image=new Image(new ByteArrayInputStream(png),256,256,true,false);if(image.isError())throw new IllegalArgumentException("Invalid item texture");decoded.put(source,image);});
                final var tintedImages=new HashMap<String,Image>();tinted.forEach((key,png)->{final var image=new Image(new ByteArrayInputStream(png),256,256,true,false);if(image.isError())throw new IllegalArgumentException("Invalid tinted item texture");tintedImages.put(key,image);});
                final var meshes=new Group();
                for(final var part:new BlockMeshBuilder().build(geometry)){
                    final var image=tintedImages.getOrDefault(part.face().texture()+"#"+part.face().tintIndex(),decoded.get(part.face().source()));if(image==null)throw new IllegalArgumentException("Missing per-face texture");
                    final var material=new PhongMaterial(Color.WHITE);material.setDiffuseMap(image);material.setSpecularColor(Color.BLACK);
                    if(model.guiLight().equals("front")||!part.shade()){material.setDiffuseColor(Color.BLACK);material.setSelfIlluminationMap(image);}
                    final var mesh=new MeshView(part.mesh());mesh.setMaterial(material);mesh.setCullFace(model.kind()==InventoryItemModel.Kind.GENERATED_2D?CullFace.NONE:CullFace.BACK);meshes.getChildren().add(mesh);
                }
                final var gui=model.gui();final var rotation=gui.rotation();final var right=gui.rightRotation();final var translation=gui.translation();final var scale=gui.scale();
                meshes.getTransforms().setAll(new Translate(translation.x()*16,-translation.y()*16,-translation.z()*16),new Rotate(rotation.x(),Rotate.X_AXIS),new Rotate(-rotation.y(),Rotate.Y_AXIS),new Rotate(-rotation.z(),Rotate.Z_AXIS),new Scale(scale.x(),scale.y(),scale.z()),new Rotate(right.x(),Rotate.X_AXIS),new Rotate(-right.y(),Rotate.Y_AXIS),new Rotate(-right.z(),Rotate.Z_AXIS));
                final var placement=new Group(meshes);placement.getTransforms().setAll(new Translate(size/2.0,size/2.0,0),new Scale(size/16.0,size/16.0,size/16.0));
                final var light=new PointLight(Color.color(0.65,0.65,0.65));light.setTranslateX(-size);light.setTranslateY(-2*size);light.setTranslateZ(-3*size);
                root.getChildren().setAll(placement,new AmbientLight(Color.color(0.55,0.55,0.55)),light);
                final var parameters=new SnapshotParameters();parameters.setFill(Color.TRANSPARENT);
                scene.snapshot(snapshot->{try{final var image=snapshot.getImage();final int[] pixels=new int[size*size];image.getPixelReader().getPixels(0,0,size,size,PixelFormat.getIntArgbInstance(),pixels,0,size);result.complete(new Pixels(size,size,pixels));}catch(Exception error){result.completeExceptionally(error);}finally{root.getChildren().clear();busy=false;}return null;},parameters,new WritableImage(size,size));
            }catch(Exception error){busy=false;if(root!=null)root.getChildren().clear();result.completeExceptionally(error);}
        });return result;
    }
    private BlockGeometryModel plane(final InventoryItemModel model){
        final var source=model.layers().layers().getFirst().source();final var face=new Face(model.itemId(),source,new UV(0,0,16,16),0,null,-1);
        return new BlockGeometryModel(new ResourceLocation(model.itemId().namespace(),"item/"+model.itemId().path()),List.of(new Element(new Vector(0,0,8),new Vector(16,16,8),Map.of(Direction.SOUTH,face),null,false)),model.sources());
    }
}
