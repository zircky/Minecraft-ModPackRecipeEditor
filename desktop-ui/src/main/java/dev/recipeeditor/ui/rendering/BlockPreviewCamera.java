package dev.recipeeditor.ui.rendering;

import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Rotate;

/** Bounded orbit state independent of drag rate and viewport size. */
public final class BlockPreviewCamera {
    private final Rotate pitch=new Rotate(25,Rotate.X_AXIS),yaw=new Rotate(-35,Rotate.Y_AXIS);
    private final PerspectiveCamera camera=new PerspectiveCamera(true);
    private double distance=55;
    public BlockPreviewCamera(){camera.setNearClip(0.1);camera.setFarClip(2000);camera.setFieldOfView(35);reset();}
    public PerspectiveCamera camera(){return camera;}
    public Rotate pitch(){return pitch;}public Rotate yaw(){return yaw;}
    public double distance(){return distance;}
    public void orbit(final double dx,final double dy){yaw.setAngle((yaw.getAngle()+dx*0.5)%360);pitch.setAngle(Math.clamp(pitch.getAngle()-dy*0.5,-85,85));}
    public void zoom(final double delta){distance=Math.clamp(distance*Math.exp(-Math.clamp(delta,-1000,1000)*0.002),24,240);camera.setTranslateZ(-distance);}
    public void reset(){pitch.setAngle(25);yaw.setAngle(-35);distance=55;camera.setTranslateZ(-distance);}
}
