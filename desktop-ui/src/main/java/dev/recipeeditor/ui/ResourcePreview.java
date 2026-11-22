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
    private java.util.concurrent.CompletableFuture<?> inventoryTask;
    private javafx.beans.InvalidationListener inventoryListener;
    ResourcePreview(final RegistryEntry entry,final dev.recipeeditor.recipeengine.DiscoveryResult discovery,final TextureResourceService textures,
            final int size,final BooleanSupplier current,final Consumer<TextureResourceService.Preview> completed){this(entry,discovery,textures,size,current,completed,"");}
    ResourcePreview(final RegistryEntry entry,final dev.recipeeditor.recipeengine.DiscoveryResult discovery,final TextureResourceService textures,
            final int size,final BooleanSupplier current,final Consumer<TextureResourceService.Preview> completed,final String nbt){
        getStyleClass().add("inventory-preview");setMinSize(size,size);setPrefSize(size,size);setMaxSize(size,size);getChildren().add(new Label("?"));
        final var provider=dev.recipeeditor.ui.rendering.InventoryPreviewProvider.shared();
        final Runnable refresh=()->{
            if(getScene()==null||!current.getAsBoolean())return;if(inventoryTask!=null)inventoryTask.cancel(false);
            getChildren().setAll(new Label("?"));final var task=provider.preview(entry,discovery.registry(),discovery.selectedRoot(),size,nbt);inventoryTask=task;
            task.whenComplete((image,error)->Platform.runLater(()->{
                if(inventoryTask!=task||getScene()==null||!current.getAsBoolean()||task.isCancelled())return;
                getProperties().put("inventory.preview",image);
                final byte[] png=image==null?new byte[0]:image.png();final String diagnostic=image==null?"Inventory preview timed out or was cancelled":image.source()+": "+image.diagnostic();
                final var decoded=png.length==0?null:new Image(new ByteArrayInputStream(png),size,size,true,false);
                if(decoded!=null&&!decoded.isError())getChildren().setAll(new ImageView(decoded));else getChildren().setAll(new Label("?"));
                Tooltip.install(this,new Tooltip(diagnostic));completed.accept(new TextureResourceService.Preview(png,png.length==0?ResourceVisual.Status.RENDERING_UNSUPPORTED:ResourceVisual.Status.READY,diagnostic));
            }));
        };
        inventoryListener=observable->refresh.run();final var weak=new javafx.beans.WeakInvalidationListener(inventoryListener);
        sceneProperty().addListener((observable,oldScene,newScene)->{
            if(oldScene!=null)provider.revisionProperty().removeListener(weak);
            if(newScene!=null){provider.revisionProperty().addListener(weak);refresh.run();}
            else if(inventoryTask!=null)inventoryTask.cancel(false);
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
