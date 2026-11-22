package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.registry.rendering.BlockGeometryModel;
import dev.recipeeditor.registry.ResourceSource;
import java.io.ByteArrayInputStream;
import java.util.*;
import javafx.application.Platform;
import javafx.application.ConditionalFeature;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.transform.Translate;

/** One interactive SubScene; receives geometry and decoded texture bytes prepared off-thread. */
public final class BlockPreview3DView extends BorderPane {
    private final BlockPreviewCamera orbit=new BlockPreviewCamera();
    private final Group geometry=new Group();
    private final SubScene scene;
    private double lastX,lastY;
    public BlockPreview3DView(final BlockGeometryModel model,final Map<ResourceSource,byte[]> textures){
        if(!Platform.isFxApplicationThread())throw new IllegalStateException("Scene graph requires FX thread");
        if(!Platform.isSupported(ConditionalFeature.SCENE3D))throw new IllegalStateException("JavaFX SCENE3D unavailable; no flat fallback presented as 3D");
        final var images=new HashMap<ResourceSource,Image>();textures.forEach((key,bytes)->{final var image=new Image(new ByteArrayInputStream(bytes),256,256,true,false);if(image.isError())throw new IllegalArgumentException("Invalid texture: "+key.internalPath());images.put(key,image);});
        final var parts=new BlockMeshBuilder().build(model);
        for(final var part:parts){
            final var image=images.get(part.face().source());if(image==null)throw new IllegalArgumentException("Missing texture bytes: "+part.face().texture());
            final var material=new PhongMaterial(Color.WHITE);material.setDiffuseMap(image);material.setSpecularColor(Color.BLACK);if(!part.shade())material.setSelfIlluminationMap(image);
            final var mesh=new MeshView(part.mesh());mesh.setMaterial(material);mesh.setCullFace(CullFace.BACK);geometry.getChildren().add(mesh);
        }
        final var bounds=geometry.getBoundsInLocal();final double extent=Math.max(bounds.getWidth(),Math.max(bounds.getHeight(),bounds.getDepth()));
        geometry.getTransforms().add(new Translate(-(bounds.getMinX()+bounds.getMaxX())/2,-(bounds.getMinY()+bounds.getMaxY())/2,-(bounds.getMinZ()+bounds.getMaxZ())/2));
        final var normalized=new Group(geometry);final double scale=16/extent;normalized.setScaleX(scale);normalized.setScaleY(scale);normalized.setScaleZ(scale);
        final var turntable=new Group(normalized);turntable.getTransforms().addAll(orbit.yaw(),orbit.pitch());
        final var light=new PointLight(Color.color(0.65,0.65,0.65));light.setTranslateX(-40);light.setTranslateY(-60);light.setTranslateZ(-70);
        final var world=new Group(turntable,new AmbientLight(Color.color(0.65,0.65,0.65)),light,orbit.camera());
        scene=new SubScene(world,640,500,true,SceneAntialiasing.BALANCED);scene.setCamera(orbit.camera());scene.setFill(Color.web("#202124"));scene.setAccessibleText("Interactive Minecraft 3D mesh");
        final var viewport=new StackPane(scene);scene.widthProperty().bind(viewport.widthProperty());scene.heightProperty().bind(viewport.heightProperty());setCenter(viewport);
        scene.setOnMousePressed(e->{lastX=e.getSceneX();lastY=e.getSceneY();});
        scene.setOnMouseDragged(e->{if(!e.isPrimaryButtonDown())return;orbit.orbit(e.getSceneX()-lastX,e.getSceneY()-lastY);lastX=e.getSceneX();lastY=e.getSceneY();});
        scene.setOnScroll(e->{orbit.zoom(e.getDeltaY());e.consume();});scene.setOnMouseClicked(e->{if(e.getClickCount()==2)orbit.reset();});
        final var reset=new Button("Reset View");reset.setOnAction(e->orbit.reset());
        final var note=new Label(model.id()+" · "+parts.size()+" faces · "+parts.size()*2+" triangles · Drag: orbit / Wheel: zoom / Double click: reset");note.setWrapText(true);
        final boolean tint=model.elements().stream().flatMap(e->e.faces().values().stream()).anyMatch(f->f.tintIndex()>=0);
        final var footer=new VBox(8,note,reset);if(tint)footer.getChildren().add(new Label("Untinted geometry: biome/runtime tint is not supplied."));setBottom(footer);
    }
    public SubScene subScene(){return scene;}public BlockPreviewCamera orbit(){return orbit;}
    public List<MeshView> meshes(){return geometry.getChildren().stream().map(n->(MeshView)n).toList();}
}
