package dev.recipeeditor.ui;

import dev.recipeeditor.ui.rendering.*;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class BlockPreview3DViewTest {
    @Test void actualSceneSnapshotChangesWithOrbitAndZoomAndResetRestoresCamera()throws Exception{
        FxLayoutTestSupport.run(()->{
            javafx.application.Platform.setImplicitExit(false);
            final var model=BlockRenderFixtures.cube(16,0,null);final var view=new BlockPreview3DView(model,BlockRenderFixtures.textures(model));
            final var stage=new Stage();stage.setScene(new Scene(view,640,520));stage.show();
            try{
                view.applyCss();view.layout();final var before=view.subScene().snapshot(null,null);
                view.orbit().orbit(130,30);final var after=view.subScene().snapshot(null,null);assertTrue(changed(before,after)>100,"A rotating mesh must produce different pixels");
                view.orbit().zoom(200);final var close=view.subScene().snapshot(null,null);assertTrue(changed(after,close)>100,"Zoom must change projected geometry");
                view.orbit().zoom(10000);assertTrue(view.orbit().distance()>=24);view.orbit().zoom(-10000);assertTrue(view.orbit().distance()<=240);
                view.orbit().reset();assertEquals(55,view.orbit().distance());assertEquals(-35,view.orbit().yaw().getAngle());assertEquals(25,view.orbit().pitch().getAngle());
            }finally{stage.close();}
        });
    }
    private long changed(final WritableImage a,final WritableImage b){long pixels=0;for(int y=0;y<(int)a.getHeight();y++)for(int x=0;x<(int)a.getWidth();x++)if(a.getPixelReader().getArgb(x,y)!=b.getPixelReader().getArgb(x,y))pixels++;return pixels;}
}
