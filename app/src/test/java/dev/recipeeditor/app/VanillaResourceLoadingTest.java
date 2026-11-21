package dev.recipeeditor.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
final class VanillaResourceLoadingTest {
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void genuineLocalClientAssetsResolveModelsTranslationsAndPngs() throws Exception {
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var textures=new TextureResourceService()) {
            final var pack=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES).modpack();
            final var result=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);
            final var evidence=new ArrayList<Map<String,Object>>();
            for(final String name:List.of("diamond","iron_ingot","gold_ingot","stone","oak_planks","crafting_table","tnt","gunpowder")) {
                final var id=ResourceLocation.parse("minecraft:"+name);
                final var item=result.registry().find(id).stream().filter(entry->entry.kind()==ResourceKind.ITEM).findFirst().orElseThrow();
                final var preview=textures.preview(item.visual(),pack.selectedDirectory());
                assertTrue(item.localizedNames().containsKey("en_us"),id+" English name missing");
                assertTrue(item.localizedNames().containsKey("ru_ru"),id+" Russian name missing");
                assertTrue(preview.png().length>0,id+": "+preview.explanation());
                assertTrue(item.sources().stream().anyMatch(source->source.internalPath().equals("assets/minecraft/models/item/"+name+".json")),"Item model missing: "+id);
                evidence.add(Map.of("id",id.toString(),"names",item.localizedNames(),"translationKey",item.localizationKey(),"model",true,"textureBytes",preview.png().length,
                        "previewStatus",preview.status().name(),"source",item.visual().layers().getFirst().source().toString(),"runtimeConfirmed",false));
            }
            final var planks=result.registry().find(ResourceLocation.parse("ad_astra:aeronos_planks")).stream().filter(entry->entry.kind()==ResourceKind.ITEM).findFirst().orElseThrow();
            assertTrue(planks.localizedNames().containsKey("en_us"),"Ad Astra block item must use its actual block translation key");
            Files.createDirectories(Path.of("build"));new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("build/vanilla-resource-evidence.json").toFile(),evidence);
        }
    }
}
