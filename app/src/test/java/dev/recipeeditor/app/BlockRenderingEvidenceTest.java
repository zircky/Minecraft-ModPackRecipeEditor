package dev.recipeeditor.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.*;
import dev.recipeeditor.ui.rendering.BlockPreview3DView;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Real-model rendering evidence, distinct from manual OS input verification. */
@Tag("external")
final class BlockRenderingEvidenceTest {
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void renderFiveGenuineVanillaModelsToMeshesAndComparePerspectives()throws Exception{
        final var ready=new CompletableFuture<Void>();try{Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException running){ready.complete(null);}ready.get(30,TimeUnit.SECONDS);
        final Path directory=Path.of(System.getProperty("realModpack")),output=Files.createDirectories(Path.of("build/phase18-render-evidence"));
        final var rows=new ArrayList<Map<String,Object>>();
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var textures=new TextureResourceService()){
            final var pack=importer.start(directory,p->{}).result().get(2,TimeUnit.MINUTES).modpack();final var result=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);final var parser=new BlockModelParser(result.registry().assetCatalog());
            for(final String name:List.of("stone","crafting_table","furnace","oak_stairs","oak_slab")){
                final var model=parser.parse(ResourceLocation.parse("minecraft:block/"+name));final var images=new HashMap<ResourceSource,byte[]>();
                for(final var element:model.elements())for(final var face:element.faces().values())if(!images.containsKey(face.source())){final var bytes=textures.load(face.source(),directory);assertTrue(bytes.length>0,face.texture().toString());images.put(face.source(),bytes);}
                final var task=new FutureTask<Void>(()->{
                    final var view=new BlockPreview3DView(model,images);final var stage=new Stage();stage.setTitle("Phase 18 genuine model evidence: "+name);stage.setScene(new Scene(view,800,640));stage.show();
                    try{
                        view.applyCss();view.layout();final var initial=view.subScene().snapshot(null,null);save(output.resolve(name+"-default.png"),initial);
                        view.orbit().orbit(260,35);final var rotated=view.subScene().snapshot(null,null);save(output.resolve(name+"-rotated.png"),rotated);final long orbitPixels=difference(initial,rotated);assertTrue(orbitPixels>100,name+" rotation did not change pixels");
                        view.orbit().zoom(180);final var zoom=view.subScene().snapshot(null,null);save(output.resolve(name+"-zoom.png"),zoom);assertTrue(difference(rotated,zoom)>100,name+" zoom did not change pixels");view.orbit().reset();assertEquals(55,view.orbit().distance());
                        final int faces=view.meshes().size();if(name.equals("stone"))assertEquals(6,faces);
                        if(name.equals("oak_slab"))assertEquals(8,model.elements().getFirst().to().y()-model.elements().getFirst().from().y());
                        if(name.equals("oak_stairs"))assertTrue(model.elements().size()>1);
                        rows.add(Map.of("model",model.id().toString(),"elements",model.elements().size(),"mesh_views",faces,"triangles",faces*2,"textures",images.size(),"rotation_changed_pixels",orbitPixels,"zoom_changed_pixels",difference(rotated,zoom),"sources",model.sources().stream().map(s->s.container()+"!"+s.internalPath()).toList()));
                    }finally{stage.close();}return null;
                });Platform.runLater(task);task.get(45,TimeUnit.SECONDS);
            }
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("metrics.json").toFile(),rows);
    }
    private long difference(final WritableImage a,final WritableImage b){long count=0;for(int y=0;y<(int)a.getHeight();y++)for(int x=0;x<(int)a.getWidth();x++)if(a.getPixelReader().getArgb(x,y)!=b.getPixelReader().getArgb(x,y))count++;return count;}
    private void save(final Path file,final WritableImage image)throws Exception{final var result=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<result.getHeight();y++)for(int x=0;x<result.getWidth();x++)result.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(result,"png",file.toFile());}
}
