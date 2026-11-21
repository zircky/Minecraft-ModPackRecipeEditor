package dev.recipeeditor.exporters;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class KubeJsExportCapabilityTest {
    @TempDir Path root;
    @Test void namespaceAndSourceOwnershipDoNotRestrictVanillaOnTargetBuild()throws Exception{
        final var f=new ExportFixtures(root);final var helper=new KubeJsTestSupport();final var change=f.create("minecraft:tnt",f.raw);
        for(final var mode:KubeJsExportMode.values()){final var c=helper.context(f,List.of(change),List.of(),mode);assertTrue(new KubeJsRecipeExporter().checkCapability(change,c).supported(),mode.toString());}
    }
    @Test void unsupportedTypesConditionsNestedExtensionsAndMissingVersionsAreRejected()throws Exception{
        final var f=new ExportFixtures(root);final var helper=new KubeJsTestSupport();
        for(final String json:List.of("{\"type\":\"gtceu:example\"}",f.raw.substring(0,f.raw.length()-1)+",\"forge:conditions\":[]}",f.raw.replace("\"tag\":\"forge:ingots/iron\"","\"type\":\"forge:nbt\",\"item\":\"minecraft:iron_ingot\",\"nbt\":{}"))){
            final var change=f.create("minecraft:test",json);for(final var mode:List.of(KubeJsExportMode.DATA_JSON,KubeJsExportMode.JAVASCRIPT)){final var c=helper.context(f,List.of(change),List.of(),mode);assertFalse(new KubeJsRecipeExporter().checkCapability(change,c).supported());}
        }
        final var change=f.create("test:new",f.raw);final var absent=f.context(List.of(change),List.of(),ExportFormat.KUBEJS,Map.of(),List.of(),OverwritePolicy.REFUSE);assertFalse(new KubeJsRecipeExporter().checkCapability(change,absent).supported());
    }
}
