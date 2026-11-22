package dev.recipeeditor.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.ui.rendering.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.concurrent.*;
import javafx.application.Platform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
final class InventoryRenderingEvidenceTest {
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void genuineInventoryModelsProduceSharedIconsRatherThanParticleSamples()throws Exception{
        final var ready=new CompletableFuture<Void>();try{Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException running){ready.complete(null);}ready.get(30,TimeUnit.SECONDS);
        final Path root=Path.of(System.getProperty("realModpack")),output=Files.createDirectories(Path.of("build/inventory-evidence"));final var rows=new ArrayList<Map<String,Object>>();
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var provider=new InventoryPreviewProvider()){
            final var pack=importer.start(root,p->{}).result().get(2,TimeUnit.MINUTES).modpack();final var result=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);final var index=result.registry();
            final var sheet=new BufferedImage(5*140,2*110,BufferedImage.TYPE_INT_ARGB);final var graphics=sheet.createGraphics();graphics.setColor(new java.awt.Color(32,33,36));graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());int offset=0;
            for(final String name:List.of("stone","oak_planks","oak_stairs","oak_slab","oak_fence","oak_door","crafting_table","furnace","diamond","iron_ingot")){
                final var id=ResourceLocation.parse("minecraft:"+name);final var entry=index.find(id).stream().filter(e->e.kind()==ResourceKind.ITEM).findFirst().orElseThrow();
                final var preview=provider.preview(entry,index,root,64).get(15,TimeUnit.SECONDS);assertTrue(preview.png().length>0,id+": "+preview.diagnostic());
                final String expected=Set.of("oak_door","diamond","iron_ingot").contains(name)?"STATIC_ITEM_MODEL_2D":"STATIC_ITEM_MODEL_3D";assertEquals(expected,preview.source(),id.toString());
                Files.write(output.resolve(name+".png"),preview.png());final var image=ImageIO.read(new ByteArrayInputStream(preview.png()));assertEquals(64,image.getWidth());assertEquals(64,image.getHeight());assertEquals(0,image.getRGB(0,0)>>>24,"Inventory icon background must remain transparent: "+id);
                graphics.drawImage(image,(offset%5)*140+38,(offset/5)*110+5,null);graphics.setColor(java.awt.Color.WHITE);graphics.drawString(name,(offset%5)*140+4,(offset/5)*110+90);offset++;
                rows.add(Map.of("id",id.toString(),"source",preview.source(),"diagnostic",preview.diagnostic(),"png_bytes",preview.png().length));
            }
            graphics.dispose();ImageIO.write(sheet,"PNG",output.resolve("static-inventory.png").toFile());
            for(final var mod:pack.mods().stream().map(mod->mod.metadata().id()).filter(id->Set.of("ad_astra","gtceu","botania","create","mekanism","nuclearcraft").contains(id)).distinct().toList()){
                final var candidates=index.entries(ResourceKind.ITEM).stream().filter(e->e.id().namespace().equals(mod)&&!index.assetCatalog().modelDefinitions(new ResourceLocation(mod,"item/"+e.id().path())).isEmpty()).limit(3).toList();
                for(final var entry:candidates){final var preview=provider.preview(entry,index,root,64).get(15,TimeUnit.SECONDS);rows.add(Map.of("id",entry.id().toString(),"source",preview.source(),"diagnostic",preview.diagnostic(),"png_bytes",preview.png().length));if(preview.png().length>0)Files.write(output.resolve(mod+"-"+entry.id().path().replace('/','_')+".png"),preview.png());}
            }
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("static-metrics.json").toFile(),rows);
    }
}
