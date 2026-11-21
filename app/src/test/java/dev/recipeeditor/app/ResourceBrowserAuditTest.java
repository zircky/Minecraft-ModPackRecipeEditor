package dev.recipeeditor.app;

import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.registry.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
final class ResourceBrowserAuditTest {
    private record ImageResult(boolean available,ResourceVisual.Status status) { }
    private final Map<ResourceVisual,ImageResult> verified=new HashMap<>();
    @Test @EnabledIfSystemProperty(named="resourceAuditDirectory",matches=".+")
    void countActualPreviewsAndNamesOnTheRealPack()throws Exception{
        final var directory=Files.createDirectories(Path.of(System.getProperty("resourceAuditDirectory")));
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService()){
            final var pack=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES).modpack();
            final var discovery=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);
            final var rows=new ArrayList<Map<String,Object>>();measure("static",discovery.registry(),pack.selectedDirectory(),rows);
            final String snapshot=System.getProperty("resourceAuditSnapshot");
            if(snapshot!=null){final var data=new RuntimeSnapshotService().load(Path.of(snapshot));final var merged=new RegistryMergeService().merge(discovery.registry(),data,Path.of(snapshot));measure("runtime",merged.preferred(),pack.selectedDirectory(),rows);}
            assertFalse(rows.isEmpty());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve(System.getProperty("resourceAuditLabel","baseline")+".json").toFile(),rows);
        }
    }
    private void measure(final String scope,final RegistryIndex index,final Path root,final List<Map<String,Object>> rows){
        try(final var textures=new TextureResourceService()){
            for(final var kind:ResourceKind.values()){
                final var entries=index.entries(kind);int images=0,en=0,ru=0;final var statuses=new TreeMap<String,Integer>();
                for(final var entry:entries){final var image=verify(entry.visual(),textures,root);if(image.available())images++;statuses.merge(image.status().name(),1,Integer::sum);if(entry.localizedNames().containsKey("en_us"))en++;if(entry.localizedNames().containsKey("ru_ru")||entry.localizedNames().containsKey("en_us"))ru++;}
                rows.add(Map.of("scope",scope,"kind",kind.name(),"resources",entries.size(),"images",images,"names_en_us",en,"names_ru_ru_with_en_fallback",ru,"preview_statuses",statuses));
            }
            if(!index.materials().isEmpty()) {
                int images=0,en=0,ru=0;
                for(final var material:index.materials()) {
                    if(material.localizedNames().containsKey("en_us"))en++;
                    if(material.localizedNames().containsKey("ru_ru")||material.localizedNames().containsKey("en_us"))ru++;
                    if(material.forms().stream().map(form->index.find(form.resourceId()).stream().filter(entry->entry.kind()==form.kind()).findFirst()).flatMap(Optional::stream).anyMatch(entry->verify(entry.visual(),textures,root).available()))images++;
                }
                rows.add(Map.of("scope",scope,"kind","MATERIAL","resources",index.materials().size(),"images",images,"names_en_us",en,"names_ru_ru_with_en_fallback",ru));
            }
        }
    }
    private ImageResult verify(final ResourceVisual visual,final TextureResourceService service,final Path root) {
        return verified.computeIfAbsent(visual,key->{final var preview=service.preview(key,root);return new ImageResult(preview.png().length>0,preview.status());});
    }
}
