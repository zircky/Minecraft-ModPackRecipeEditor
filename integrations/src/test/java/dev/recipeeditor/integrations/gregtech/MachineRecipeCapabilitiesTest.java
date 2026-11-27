package dev.recipeeditor.integrations.gregtech;

import dev.recipeeditor.companionprotocol.RuntimeData.*;
import com.google.gson.JsonObject;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MachineRecipeCapabilitiesTest {
    @Test void discoversAddonMachineOnceAndReadsOnlyCapturedLimits() {
        final var machine=new Registry(Kind.RECIPE_TYPE,"addon:custom_machine","Custom machine","addon.GTType",
                Map.of("gt.machineVersion","7.1.4","gt.limit.inputs.item","6","gt.limit.outputs.fluid","invalid",
                        "gt.limit.outputs.item","1"),Map.of());
        final var recipe=new Recipe("test:one","addon:custom_machine","gtceu:machine","gt.Recipe","gtceu-7.1.4",
                Support.PARTIALLY_SUPPORTED,new JsonObject(),"runtime",List.of());
        final var snapshot=new Snapshot(1,"fixture",true,new Environment("session","source","1.20.1","FORGE",
                "47.4.28","0.1.0",1,"2026-11-26T00:00:00Z"),List.of(machine),List.of(),List.of(recipe),List.of(),List.of());
        final var rows=new GregTechMachineCatalog().load(snapshot);
        assertEquals(1,rows.size());
        assertEquals("addon:custom_machine",rows.getFirst().id().toString());
        assertEquals("UNKNOWN",rows.getFirst().sourceMod());
        assertEquals(Map.of("inputs.item",6,"outputs.item",1),rows.getFirst().limits());
    }
}
