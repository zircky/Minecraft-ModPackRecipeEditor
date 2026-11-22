package dev.recipeeditor.app;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.*;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.ui.UiTheme;
import dev.recipeeditor.ui.rendering.BlockPreview3DView;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Reproducible visual harness; production view, genuine local models, no synthetic resource IDs. */
public final class BlockRenderingPreview {
    public static void main(final String[] args){Application.launch(Preview.class,args);}
    public static final class Preview extends Application {
        private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),Thread.ofVirtual().name("block-model-preview-",0).factory());
        private final TextureResourceService textures=new TextureResourceService();
        private final BorderPane root=new BorderPane();
        private long generation;
        @Override public void start(final Stage stage){
            stage.setTitle("Minecraft Recipe Editor — interactive 3D model verification");new UiTheme().apply(new Scene(root,1100,800));stage.setScene(root.getScene());root.setCenter(new Label("Loading genuine Minecraft assets off the UI thread…"));stage.show();
            worker.execute(()->{
                try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService()){
                    final Path directory=Path.of(getParameters().getRaw().getFirst());
                    final var pack=importer.start(directory,p->{}).result().get(2,TimeUnit.MINUTES).modpack();
                    final var result=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);final var parser=new BlockModelParser(result.registry().assetCatalog());
                    Platform.runLater(()->{
                        final var select=new ComboBox<String>();select.getItems().setAll("stone","crafting_table","furnace","oak_stairs","oak_slab");select.setAccessibleText("Minecraft 3D model");
                        root.setTop(new ToolBar(new Label("Real Minecraft Model JSON → TriangleMesh"),select));select.valueProperty().addListener((o,a,b)->load(parser,ResourceLocation.parse("minecraft:block/"+b),directory));select.getSelectionModel().selectFirst();
                    });
                }catch(Exception error){Platform.runLater(()->root.setCenter(new TextArea(error.toString())));}
            });
        }
        private void load(final BlockModelParser parser,final ResourceLocation id,final Path directory){
            final long request=++generation;root.setCenter(new Label("Parsing "+id+"…"));
            try{worker.execute(()->{
                try{
                    final var model=parser.parse(id);final var bytes=new HashMap<ResourceSource,byte[]>();
                    for(final var element:model.elements())for(final var face:element.faces().values())if(!bytes.containsKey(face.source())){final var data=textures.load(face.source(),directory);if(data.length==0)throw new IllegalArgumentException("Texture unavailable: "+face.texture());bytes.put(face.source(),data);}
                    Platform.runLater(()->{if(request==generation)try{root.setCenter(new BlockPreview3DView(model,bytes));}catch(Exception error){root.setCenter(new TextArea(error.toString()));}});
                }catch(Exception error){Platform.runLater(()->{if(request==generation)root.setCenter(new TextArea(error.toString()));});}
            });}catch(RejectedExecutionException busy){root.setCenter(new Label("Model queue busy; reselect a model."));}
        }
        @Override public void stop(){generation++;worker.shutdownNow();textures.close();}
    }
}
