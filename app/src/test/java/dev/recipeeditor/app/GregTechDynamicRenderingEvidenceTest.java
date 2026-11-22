package dev.recipeeditor.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.ui.rendering.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import javafx.application.Platform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Actual registered GT8 forms, current material registry and native-vs-static images. */
@Tag("external")
final class GregTechDynamicRenderingEvidenceTest {
    @Test @EnabledIfSystemProperty(named="previewSession",matches=".+")
    void actualGtMaterialAndOreModelsRenderOfflineAndThroughMinecraft()throws Exception{
        final var ready=new CompletableFuture<Void>();try{Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException started){ready.complete(null);}ready.get(30,TimeUnit.SECONDS);
        final Path output=Files.createDirectories(Path.of("build/phase19-evidence")),root=Path.of(System.getProperty("realModpack"));final var mapper=new ObjectMapper();
        final RuntimeData.Snapshot snapshot;
        try(final var client=new CompanionConnectionService()){
            client.connect(Path.of(System.getProperty("previewSession"))).get(10,TimeUnit.SECONDS);assertTrue(client.itemPreviewsAvailable());final String saved=System.getProperty("phase19Snapshot");final var full=saved==null?client.synchronize(p->{}).get(180,TimeUnit.SECONDS):new RuntimeSnapshotService().load(Path.of(saved));
            new RuntimeSnapshotService().save(output.resolve("verified-materials.mresnapshot"),full);
            snapshot=new RuntimeData.Snapshot(full.schemaVersion(),full.snapshotId(),true,full.environment(),full.registries(),List.of(),List.of(),full.mods(),full.diagnostics(),full.materials());
        }
        final var snapshotFile=output.resolve("verified-materials.mresnapshot");
        final var selected=new LinkedHashSet<String>();
        for(final var material:snapshot.materials()){
            final var forms=mapper.readTree(material.metadata().getOrDefault("preview.forms","{}"));
            if(Set.of("gtceu:steel","gtceu:bronze").contains(material.id()))for(final var form:material.forms())if(form.kind()==RuntimeData.Kind.ITEM&&form.resourceId().startsWith("gtceu:")&&Set.of("gtceu:ingot","gtceu:dust","gtceu:plate").contains(form.prefix()))selected.add(form.resourceId());
            if(material.id().equals("gtceu:green_sapphire"))for(final var form:material.forms()){
                final String base=forms.path(form.resourceId()).path("oreBaseModel").asText();
                if(form.kind()==RuntimeData.Kind.ITEM&&form.resourceId().startsWith("gtceu:")&&List.of("stone","deepslate","netherrack","end_stone").stream().anyMatch(s->base.equals("minecraft:block/"+s)))selected.add(form.resourceId());
            }
        }
        assertTrue(selected.size()>=6,"Actual steel forms and at least three rock variants required: "+selected);
        final var rows=new ArrayList<Map<String,Object>>();final var sheet=new BufferedImage(640,selected.size()*110,BufferedImage.TYPE_INT_ARGB);final var graphics=sheet.createGraphics();graphics.setColor(new java.awt.Color(32,33,36));graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());int row=0;
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var provider=new InventoryPreviewProvider();final var client=new CompanionConnectionService()){
            final var pack=importer.start(root,p->{}).result().get(2,TimeUnit.MINUTES).modpack();final var original=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);final var index=new RegistryMergeService().merge(original.registry(),snapshot,snapshotFile).preferred();
            client.connect(Path.of(System.getProperty("previewSession"))).get(10,TimeUnit.SECONDS);
            for(final var text:selected){
                final var id=dev.recipeeditor.core.ResourceLocation.parse(text);final var entry=index.find(id).stream().filter(e->e.kind()==ResourceKind.ITEM).findFirst().orElseThrow();
                final var offline=provider.preview(entry,index,root,64).get(15,TimeUnit.SECONDS);assertTrue(offline.png().length>0,text+": "+offline.diagnostic());assertTrue(offline.diagnostic().contains("GT8 material template"),"Dynamic template must actually be used: "+text);
                final var nativeIcon=client.itemPreviews(List.of(new ItemPreviewRequest(text,64))).get(10,TimeUnit.SECONDS).getFirst();assertEquals("READY",nativeIcon.status(),nativeIcon.diagnostic());
                final String file=text.replace(':','_').replace('/','_');Files.write(output.resolve(file+"-static.png"),offline.png());Files.write(output.resolve(file+"-native.png"),nativeIcon.png());
                graphics.setColor(java.awt.Color.WHITE);graphics.drawString(text,8,row*110+18);graphics.drawString("Native",340,row*110+18);graphics.drawString("Static",460,row*110+18);
                graphics.drawImage(ImageIO.read(new ByteArrayInputStream(nativeIcon.png())),340,row*110+30,null);graphics.drawImage(ImageIO.read(new ByteArrayInputStream(offline.png())),460,row*110+30,null);row++;
                rows.add(Map.of("id",text,"staticSource",offline.source(),"staticDiagnostic",offline.diagnostic(),"nativeSource",nativeIcon.source(),"nativeStatus",nativeIcon.status()));
            }
        }
        graphics.dispose();ImageIO.write(sheet,"PNG",output.resolve("native-static-comparison.png").toFile());mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("metrics.json").toFile(),rows);
    }
}
