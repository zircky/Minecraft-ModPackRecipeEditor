package dev.recipeeditor.exporters;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ExportSourceMappingTest {
    @TempDir Path root;
    @Test void plannerKeepsNamespaceAndBothPhysicalDefinitionsWithoutInventingRuntimeWinner() throws Exception {
        final var fixture=new ExportFixtures(root);final var id=ResourceLocation.parse("minecraft:tnt");
        final String internal="data/minecraft/recipes/tnt.json";
        final var jar=fixture.source.resolve("bop.jar");
        try(final var zip=new ZipOutputStream(Files.newOutputStream(jar))){zip.putNextEntry(new ZipEntry(internal));zip.write(fixture.raw.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        final var source=new RecipeSource(SourceType.MOD_JAR,List.of("biomesoplenty"),jar,internal,"Static resource scan");
        final var base=fixture.validator.validate(id,fixture.raw,source).recipe();
        final var pack=Files.createDirectories(fixture.source.resolve("datapacks/custom"));Files.createDirectories(pack.resolve("data/minecraft/recipes"));Files.writeString(pack.resolve(internal),fixture.raw);
        final var second=fixture.validator.validate(id,fixture.raw,new RecipeSource(SourceType.DATAPACK,List.of(),pack,internal,"Local static resource scan")).recipe();
        final var edited=fixture.validator.validate(id,fixture.raw.replace("\"count\":2","\"count\":3"),source).recipe();
        final var change=new RecipeChange(id,RecipeChangeType.UPDATE,List.of(OriginalRecipeReference.from(base),OriginalRecipeReference.from(second)),List.of(edited),Instant.EPOCH,Map.of());
        final var context=fixture.context(List.of(change),List.of(base,second),ExportFormat.DATAPACK,Map.of(),List.of(),OverwritePolicy.REFUSE);
        final var plan=fixture.planner.plan(fixture.request(context));assertTrue(plan.valid(),plan.diagnostics().toString());
        final var planned=plan.recipes().getFirst();final var provenance=planned.provenance();
        assertEquals("minecraft",provenance.recipeNamespace());assertEquals(Set.of("biomesoplenty"),provenance.sourceModIds());
        assertEquals(2,provenance.discoveredDefinitions().size());assertEquals(2,provenance.overriddenDefinitions().size());
        assertEquals(fixture.project,provenance.activeDefinition().orElseThrow().archivePath());
        assertEquals(change,planned.change());assertEquals("UNKNOWN",provenance.declaringSource());
        assertTrue(provenance.describe().contains(jar.toString()));assertTrue(provenance.describe().contains(pack.toString()));
        assertTrue(provenance.describe().contains(internal));assertTrue(provenance.activeSource().contains("runtime winner UNKNOWN"));
        assertTrue(plan.files().keySet().stream().anyMatch(path->path.endsWith("data/minecraft/recipes/tnt.json")));
        assertFalse(plan.files().keySet().stream().anyMatch(path->path.contains("data/biomesoplenty/")));
    }
    @Test void deletionRetainsAllSourcesAndHasNoActiveProjectDefinition() throws Exception {
        final var fixture=new ExportFixtures(root);final var base=fixture.original("minecraft:example",fixture.raw);
        final var change=fixture.change(base,RecipeChangeType.DELETE,"");
        final var provenance=new ExportSourceMapping().resolve(change,new RecipeIndex(List.of(base),new TagIndex(List.of())),fixture.project);
        assertTrue(provenance.activeDefinition().isEmpty());assertEquals(List.of(base.source()),provenance.overriddenDefinitions());
        assertEquals(List.of(base.source()),provenance.discoveredDefinitions());
    }
    @Test void blockedChangeCannotClaimAnActiveOrOverriddenDefinition() throws Exception {
        final var fixture=new ExportFixtures(root);final var base=fixture.original("minecraft:tnt",fixture.raw);
        final var change=fixture.change(base,RecipeChangeType.UPDATE,fixture.raw);
        final var context=fixture.context(List.of(change),List.of(base),ExportFormat.DATAPACK,Map.of(),List.of(),OverwritePolicy.REFUSE);
        final var blocked=new ExportContext(context.modpack(),context.originals(),context.savedChanges(),context.projectFile(),context.profile(),List.of(),Set.of(change.id()),false);
        final var plan=fixture.planner.plan(fixture.request(blocked));assertFalse(plan.valid());
        final var provenance=plan.recipes().getFirst().provenance();
        assertEquals(RecipeProvenance.Scope.PROJECT_CONFLICT,provenance.scope());assertTrue(provenance.activeDefinition().isEmpty());
        assertTrue(provenance.overriddenDefinitions().isEmpty());assertEquals(List.of(base.source()),provenance.discoveredDefinitions());
        assertTrue(provenance.activeSource().contains("UNKNOWN"));
    }
}
