package dev.recipeeditor.ui;

import dev.recipeeditor.registry.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;

/** Loads only mounted resource cells, sharing the application's bounded texture worker/cache. */
final class ResourcePreview extends StackPane {
    ResourcePreview(final ResourceVisual visual,final Path root,final TextureResourceService textures,
            final int size,final BooleanSupplier current,final Consumer<TextureResourceService.Preview> completed) {
        setMinSize(size,size);setPrefSize(size,size);setMaxSize(size,size);
        final var placeholder=new Label("?");getChildren().add(placeholder);
        Tooltip.install(this,new Tooltip(visual.status()+"\n"+visual.explanation()));
        sceneProperty().addListener((observable,oldScene,newScene)->{
            if(newScene!=null)load(visual,root,textures,size,current,completed);
        });
    }
    private void load(final ResourceVisual visual,final Path root,final TextureResourceService textures,
            final int size,final BooleanSupplier current,final Consumer<TextureResourceService.Preview> completed) {
        textures.previewAsync(visual,root).thenAccept(preview->{
            final byte[] bytes=preview.png();
            final var image=bytes.length==0?null:new Image(new ByteArrayInputStream(bytes),size,size,true,false);
            Platform.runLater(()->{
                if(!current.getAsBoolean())return;
                Tooltip.install(this,new Tooltip(preview.status()+"\n"+preview.explanation()));
                if(image!=null&&!image.isError())getChildren().setAll(new ImageView(image));
                completed.accept(preview);
            });
        });
    }
}
