package dev.recipeeditor.ui;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") final class ResourcePreviewMountTest {
    @TempDir Path root;
    @Test void cachedTextureLoadsWhenAnInitiallyHiddenViewIsMounted()throws Exception {
        final String path="assets/test/textures/item/sample.png";final var file=root.resolve(path);Files.createDirectories(file.getParent());
        final var image=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xffffffff);javax.imageio.ImageIO.write(image,"PNG",file.toFile());
        final var visual=new ResourceVisual(ResourceVisual.Status.READY,List.of(new ResourceVisual.Layer(new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),root,path,"fixture"),null)),"fixture");
        try(final var textures=new TextureResourceService()) {
            assertTrue(textures.previewAsync(visual,root).get(5,TimeUnit.SECONDS).png().length>0);
            final var completed=new CompletableFuture<Boolean>();final var holder=new ResourcePreview[1];
            FxLayoutTestSupport.run(()->holder[0]=new ResourcePreview(visual,root,textures,40,()->holder[0].getScene()!=null,p->completed.complete(p.png().length>0)));
            FxLayoutTestSupport.run(()->{}); // Flush any eager cache callback before attaching the view.
            assertFalse(completed.isDone());
            FxLayoutTestSupport.run(()->new javafx.scene.Scene(holder[0],100,100));
            assertTrue(completed.get(5,TimeUnit.SECONDS));
            FxLayoutTestSupport.run(()->assertInstanceOf(javafx.scene.image.ImageView.class,holder[0].getChildren().getFirst()));
        }
    }
}
