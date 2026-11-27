package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
@EnabledIfSystemProperty(named="realModpack",matches=".+")
final class BloodAltarPreviewTest {
    @Test void rasterizesTheInstalledForgeObjUsingTheSharedPreviewService() throws Exception {
        final var root=Path.of(System.getProperty("realModpack"));
        final var game=root.resolve("minecraft");
        final Path jar;
        try(final var mods=Files.list(game.resolve("mods"))) {
            jar=mods.filter(path->path.getFileName().toString().startsWith("blood-magic-")).findFirst().orElseThrow();
        }
        final var models=new ArrayList<ResourceDocument>();
        try(final var zip=new ZipFile(jar.toFile())){
            for(final String path:List.of("assets/bloodmagic/models/item/altar.json","assets/bloodmagic/models/block/bloodaltar.json")){
                try(final var stream=zip.getInputStream(zip.getEntry(path))){
                    models.add(new ResourceDocument(asset(jar,path),new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
                }
            }
        }
        final var texture=asset(jar,"assets/bloodmagic/textures/models/altar.png");
        final var item=new RegistryEntry(ResourceLocation.parse("bloodmagic:altar"),ResourceKind.ITEM,"Blood Altar","",Map.of(),
                List.of(),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"ISG");
        final var index=new RegistryIndex(List.of(item),new ResourceScanResult(models,List.of(texture),List.of()));
        try(final var previews=new InventoryPreviewProvider()){
            final var icon=previews.preview(item,index,root,64).get(10,TimeUnit.SECONDS);
            assertEquals("STATIC_ITEM_MODEL_3D",icon.source(),icon.diagnostic());
            final var image=ImageIO.read(new ByteArrayInputStream(icon.png()));
            assertEquals(0,image.getRGB(0,0)>>>24);
            final var colors=new HashSet<Integer>();
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)colors.add(image.getRGB(x,y));
            assertTrue(colors.size()>32);
            final var output=Files.createDirectories(Path.of("build","phase24-render"));
            Files.write(output.resolve("blood-altar.png"),icon.png());
        }
    }
    private ResourceSource asset(final Path jar,final String path){
        return new ResourceSource(SourceType.MOD_JAR,List.of("bloodmagic"),jar,path,"installed ISG asset");
    }
}
