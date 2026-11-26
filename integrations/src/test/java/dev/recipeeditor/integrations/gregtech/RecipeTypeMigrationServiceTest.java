package dev.recipeeditor.integrations.gregtech;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.integrations.api.IntegrationContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeTypeMigrationServiceTest {
    private final String raw="""
            {"type":"gtceu:assembler","duration":100,"inputs":{"item":[{"content":{"item":"minecraft:iron_ingot"},"chance":0}]},
             "outputs":{"item":[{"content":{"item":"minecraft:gold_ingot"}}]}}
            """;
    private final IntegrationContext verified=new IntegrationContext("1.20.1",ModLoaderType.FORGE,
            Map.of("gtceu","7.1.4","kubejs","2001.6.4-build.120"),Map.of());
    private final ResourceLocation target=ResourceLocation.parse("test:macerator/moved");
    private final ResourceLocation machine=ResourceLocation.parse("gtceu:macerator");

    @Test void preservesAllSlotsAndNonConsumableChanceAndChecksObservedLimits() {
        final var service=new RecipeTypeMigrationService();
        final var compatible=service.preview(raw,target,new GregTechMachineCatalog.Machine(machine,"Macerator","UNKNOWN",
                "7.1.4",Map.of("inputs.item",1,"outputs.item",1)),verified);
        assertTrue(compatible.compatible(),compatible.issues().toString());
        final var mapper=new GregTechRecipeMapper();
        assertEquals(mapper.object(raw).get("inputs"),mapper.object(compatible.json()).get("inputs"));
        assertEquals(mapper.object(raw).get("outputs"),mapper.object(compatible.json()).get("outputs"));
        final var incompatible=service.preview(raw,target,new GregTechMachineCatalog.Machine(machine,"Macerator","UNKNOWN",
                "7.1.4",Map.of("inputs.item",0)),verified);
        assertEquals(compatible.json(),incompatible.json());
        assertTrue(incompatible.issues().stream().anyMatch(issue->issue.contains("permits 0")));
        assertTrue(incompatible.issues().stream().anyMatch(issue->issue.contains("Unknown outputs.item")));
    }

    @Test void pickerPreservesChanceWhenReplacingAGregTechSlot() {
        final var slots=new GregTechSlotEditing();
        final var patched=slots.replace(raw,"inputs","item",0,1,"{\"tag\":\"forge:ingots\"}",3);
        final var content=new GregTechRecipeMapper().object(patched).path("inputs").path("item").get(0);
        assertEquals(0,content.path("chance").intValue());
        assertEquals(3,content.path("content").path("count").intValue());
        assertEquals("forge:ingots",content.path("content").path("ingredient").path("tag").asText());
        assertThrows(IllegalArgumentException.class,()->slots.replace(raw,"inputs","item",1,1,"{\"item\":\"minecraft:dirt\"}",1));
    }
}
