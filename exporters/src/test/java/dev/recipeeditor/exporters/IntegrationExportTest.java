package dev.recipeeditor.exporters;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("integration")
final class IntegrationExportTest {
    @TempDir Path root;
    private final String json="""
        {"type":"gtceu:assembler","duration":100,
        "inputs":{"item":[{"content":{"type":"gtceu:sized","count":2,"ingredient":{"tag":"forge:ingots/iron"}},"chance":0,"maxChance":10000,"tierChanceBoost":0}],
                  "fluid":[{"content":{"amount":100,"value":[{"tag":"forge:water"}]}}]},
        "outputs":{"item":[{"content":{"item":"minecraft:iron_ingot"},"chance":5000,"maxChance":10000,"tierChanceBoost":100}]},
        "tickInputs":{"eu":[{"content":30}]}}
        """;
    @Test void chanceFluidNonConsumableAndAllThreeOperationsUseExistingExportPlan() throws Exception {
        final var fixtures=new ExportFixtures(root);
        final var original=fixtures.original("test:assembler/old",json);
        final var update=fixtures.change(original,RecipeChangeType.UPDATE,json.replace("100,","240,"));
        final var create=fixtures.create("test:assembler/new",json);
        final var plan=fixtures.planner.plan(fixtures.request(fixtures.context(List.of(update,create),List.of(original),ExportFormat.KUBEJS,Map.of("gtceu","7.1.4","kubejs","2001.6.4-build.120"),List.of(),OverwritePolicy.REFUSE)));
        assertTrue(plan.valid(),plan.diagnostics().toString());final var script=plan.files().values().iterator().next();assertTrue(script.contains("event.remove"));assertTrue(script.contains("tierChanceBoost"));assertTrue(script.contains("forge:water"));assertTrue(script.contains("chance"));
        final var deletion=fixtures.change(original,RecipeChangeType.DELETE,"");
        assertTrue(fixtures.planner.plan(fixtures.request(fixtures.context(List.of(deletion),List.of(original),ExportFormat.KUBEJS,Map.of("gtceu","7.1.4","kubejs","2001.6.4-build.120"),List.of(),OverwritePolicy.REFUSE))).valid());
    }
    @Test void idRewriteIsRejectedAndEnergyUsesPinnedKubeJsObjectEncoding() throws Exception {
        final var mapper=new dev.recipeeditor.integrations.gregtech.GregTechRecipeMapper();
        final var exporter=new dev.recipeeditor.integrations.gregtech.GregTechRecipeExporter();
        final var model=mapper.map(dev.recipeeditor.core.ResourceLocation.parse("test:assembler/energy"),json,false);
        final String script=exporter.generate(model,"test:assembler/energy",false);
        assertTrue(script.contains("event.recipes.gtceu.assembler"));
        assertTrue(script.contains("voltage"));assertTrue(script.contains("amperage"));assertFalse(script.contains(".id("));
        assertThrows(IllegalArgumentException.class,()->exporter.generate(model,"test:renamed",false));
        assertFalse(exporter.unsupported(mapper.map(dev.recipeeditor.core.ResourceLocation.parse("test:missing_prefix"),json,false)).isEmpty());
    }
    @Test void unknownNestedPropertyAndMissingDependencyNeverPermitLossyExport() throws Exception {
        final var fixtures=new ExportFixtures(root);
        final var unknown=fixtures.create("test:assembler/custom",json.replace("\"duration\":100","\"duration\":100,\"unknown\":{\"x\":1}"));
        assertFalse(fixtures.plan(List.of(unknown),ExportFormat.KUBEJS).valid());
        final var known=fixtures.create("test:assembler/known",json);
        assertFalse(fixtures.planner.plan(fixtures.request(fixtures.context(List.of(known),List.of(),ExportFormat.KUBEJS,Map.of("gtceu","7.1.4"),List.of(),OverwritePolicy.REFUSE))).valid());
        assertFalse(fixtures.plan(List.of(known),ExportFormat.CRAFTTWEAKER).valid());
    }
}
