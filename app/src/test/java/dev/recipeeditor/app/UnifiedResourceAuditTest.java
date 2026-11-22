package dev.recipeeditor.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
final class UnifiedResourceAuditTest {
    @Test @EnabledIfSystemProperty(named="resourceAuditSnapshot",matches=".+")
    void genuinePackRetainsAssetsAndUnknownRelationshipsWithoutModelCards()throws Exception {
        final long started=System.nanoTime();
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService()){
            final var pack=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES).modpack();
            final var discovery=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);
            final var snapshotFile=Path.of(System.getProperty("resourceAuditSnapshot"));final var snapshot=new RuntimeSnapshotService().load(snapshotFile);
            final var index=new RegistryMergeService().merge(discovery.registry(),snapshot,snapshotFile).preferred();
            final var unified=new UnifiedResourceIndex(index);final var assets=index.assetCatalog();
            final var blockIds=index.entries(ResourceKind.BLOCK).stream().map(RegistryEntry::id).collect(java.util.stream.Collectors.toSet());
            final var itemIds=index.entries(ResourceKind.ITEM).stream().map(RegistryEntry::id).collect(java.util.stream.Collectors.toSet());
            final var staticBlocks=discovery.registry().entries(ResourceKind.BLOCK);
            assertTrue(staticBlocks.stream().allMatch(e->e.sources().stream().anyMatch(s->s.internalPath().contains("/blockstates/")||s.internalPath().contains("/tags/blocks/"))));
            assertEquals(itemIds.size(),unified.entries(UnifiedResourceIndex.Type.ALL).stream().filter(e->e.kind()==ResourceKind.ITEM).count());
            assertEquals(blockIds.size(),unified.entries(UnifiedResourceIndex.Type.ALL_BLOCKS).size());
            final long modelOnly=assets.records().stream().filter(a->Set.of(ResourceAssetRecord.Kind.ITEM_MODEL,ResourceAssetRecord.Kind.BLOCK_MODEL,ResourceAssetRecord.Kind.OTHER_MODEL).contains(a.kind())).filter(a->{final String path=a.id().path().replaceFirst("^(item|block)/","");final var id=new dev.recipeeditor.core.ResourceLocation(a.id().namespace(),path);return !itemIds.contains(id)&&!blockIds.contains(id);}).count();
            final long excluded=assets.records(ResourceAssetRecord.Kind.BLOCK_MODEL).stream().filter(a->!blockIds.contains(new dev.recipeeditor.core.ResourceLocation(a.id().namespace(),a.id().path().substring("block/".length())))).count();
            final var metrics=new LinkedHashMap<String,Object>();metrics.put("pack",pack.selectedDirectory().toString());metrics.put("snapshot",snapshotFile.toString());metrics.put("mods",pack.mods().size());
            metrics.put("registered_blocks",index.entries(ResourceKind.BLOCK).stream().filter(e->e.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME).count());metrics.put("registered_items",index.entries(ResourceKind.ITEM).stream().filter(e->e.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME).count());
            metrics.put("block_items",unified.entries(UnifiedResourceIndex.Type.BLOCK_ITEMS).size());metrics.put("confirmed_blocks_without_item",unified.entries(UnifiedResourceIndex.Type.BLOCKS_WITHOUT_ITEM).size());metrics.put("unknown_block_item_links",index.entries(ResourceKind.ITEM).stream().filter(e->unified.relationships().isBlockItem(e.id())&&unified.relationships().block(e.id()).isEmpty()).count());
            metrics.put("static_block_candidates",staticBlocks.size());metrics.put("asset_definitions",assets.records().size());metrics.put("model_only_definitions",modelOnly);metrics.put("excluded_block_model_definitions",excluded);
            metrics.put("elapsed_seconds",(System.nanoTime()-started)/1_000_000_000.0);metrics.put("rendered_3d_previews","NOT IMPLEMENTED: deferred to Steps 5-7");
            assertTrue(excluded>0);assertTrue(modelOnly>0);assertTrue(metrics.get("registered_blocks") instanceof Long blocks&&blocks>0);
            final var target=Path.of("build/phase17-resource-audit.json");Files.createDirectories(target.getParent());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(target.toFile(),metrics);
        }
    }
}
