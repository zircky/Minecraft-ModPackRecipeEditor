package dev.recipeeditor.app;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.SourceType;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
final class RealRecipeProvenanceTest {
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void minecraftTntIsPhysicallyDiscoveredInBiomesOPlentyWithoutInferringAuthorOrWinner() throws Exception {
        try(final var importer=new DefaultModpackImportService();final var scanner=new DefaultStaticAnalysisService()) {
            final var imported=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES);
            Assumptions.assumeTrue(imported.modpack().mods().stream().anyMatch(mod->mod.metadata().id().equals("biomesoplenty")),"This probe requires the ISG Biomes O' Plenty fixture");
            final var result=scanner.start(imported.modpack(),p->{}).result().get(5,TimeUnit.MINUTES);
            final var id=ResourceLocation.parse("minecraft:tnt");
            final var found=result.recipes().findById(id).stream().filter(recipe->recipe.source().type()==SourceType.MOD_JAR&&recipe.source().archiveModIds().contains("biomesoplenty")).toList();
            assertEquals(1,found.size());final var recipe=found.getFirst();
            assertEquals("minecraft",recipe.id().namespace());assertEquals("data/minecraft/recipes/tnt.json",recipe.source().internalPath());
            assertTrue(recipe.source().archivePath().getFileName().toString().startsWith("BiomesOPlenty-"));
            assertEquals("UNKNOWN",recipe.source().declaringSource());assertTrue(result.recipes().provenance(id).activeDefinition().isEmpty());
            assertTrue(result.recipes().bySourceMod("biomesoplenty").contains(recipe));assertTrue(result.recipes().byNamespace("minecraft").contains(recipe));
            Files.createDirectories(Path.of("build"));
            Files.writeString(Path.of("build/real-provenance-result.txt"),result.recipes().provenance(id).describe());
        }
    }
}
