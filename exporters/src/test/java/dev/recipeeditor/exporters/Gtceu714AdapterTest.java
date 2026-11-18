package dev.recipeeditor.exporters;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class Gtceu714AdapterTest {
    @TempDir Path root;
    private final String json="""
        {"type":"gtceu:chemical_reactor","duration":120,
         "inputs":{"item":[{"content":{"type":"gtceu:sized","count":2,"ingredient":{"tag":"forge:ingots/iron"}}}],"fluid":[{"content":{"amount":1000,"value":{"fluid":"minecraft:water"}}}]},
         "outputs":{"item":[{"content":{"item":"minecraft:iron_ingot"},"chance":10000,"maxChance":10000,"tierChanceBoost":0}]},
         "tickInputs":{"eu":[{"content":{"voltage":30,"amperage":1}}]}}
        """;
    @Test void completeStaticCodecSubsetIsPreservedWithoutBuilderGuesses()throws Exception {final var f=new ExportFixtures(root);final var p=f.plan(List.of(f.create("test:chemical_reactor/machine",json)),ExportFormat.KUBEJS);assertTrue(p.valid(),p.diagnostics().toString());assertEquals(ExportSupportStatus.SUPPORTED,p.recipes().getFirst().capability().properties().get("Fluid Inputs"));assertEquals(ExportSupportStatus.UNSUPPORTED,p.recipes().getFirst().capability().properties().get("Research Requirements"));assertTrue(p.files().values().iterator().next().contains("JSON.parse"));assertFalse(f.plan(List.of(f.create("test:chemical_reactor/machine",json)),ExportFormat.DATAPACK).valid());}
    @Test void rejectsUnverifiedResearchConditionsAndIncompleteOutputs()throws Exception {final var f=new ExportFixtures(root);for(final String invalid:List.of(json.replace("\"chance\":10000","\"chance\":\"10000\""),json.replace("\"duration\":120","\"duration\":120,\"data\":{\"research\":1}"),json.replace("\"duration\":120","\"duration\":120,\"recipeConditions\":[{}]"),json.replace("\"duration\":120","\"duration\":0"),"{\"type\":\"gtceu:assembler\",\"duration\":20,\"outputs\":{\"item\":[]}}"))assertFalse(f.plan(List.of(f.create("test:chemical_reactor/machine",invalid)),ExportFormat.KUBEJS).valid(),invalid);}
    @Test void neverInfersUnknownMachineOrVersion()throws Exception {final var f=new ExportFixtures(root);assertFalse(f.plan(List.of(f.create("test:chemical_reactor/machine",json.replace("gtceu:chemical_reactor","gtceu:unknown"))),ExportFormat.KUBEJS).valid());final var c=f.create("test:chemical_reactor/machine",json);assertFalse(new Gtceu714Adapter().check(c,f.context(List.of(c),List.of(),ExportFormat.KUBEJS,Map.of("gtceu","8.0.0"),List.of(),OverwritePolicy.REFUSE)).supported());}
}
