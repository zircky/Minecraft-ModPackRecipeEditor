package dev.recipeeditor.modpackparser;

import java.nio.file.Path;
import java.util.ArrayList;
import dev.recipeeditor.core.importing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

final class ModLoaderDetectorTest {
    @TempDir Path root;
    @ParameterizedTest @CsvSource({"forge,FORGE","neoforge,NEOFORGE","fabric,FABRIC","quilt,QUILT"})
    void detectsManifestLoaders(final String id, final ModLoaderType type) throws Exception {
        TestFixtures.write(root,"manifest.json","{\"minecraft\":{\"modLoaders\":[{\"id\":\""+id+"-1.2.3\"}]}}");
        final var loader = new ModLoaderDetector().detect(TestFixtures.evidence(root),new ArrayList<>());
        assertEquals(type,loader.type()); assertEquals("1.2.3",loader.version()); assertFalse(loader.sources().isEmpty());
    }
    @Test void unknownEvenWithFabricMod() throws Exception {
        TestFixtures.jar(root,"mods/mod.jar",java.util.Map.of("fabric.mod.json",TestFixtures.fabric()));
        assertEquals(ModLoaderType.UNKNOWN,new ModLoaderDetector().detect(TestFixtures.evidence(root),new ArrayList<>()).type());
    }
    @Test void vanillaRequiresExplicitComponents() throws Exception {
        TestFixtures.write(root,"mmc-pack.json","{\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.20.1\"}]}");
        assertEquals(ModLoaderType.VANILLA,new ModLoaderDetector().detect(TestFixtures.evidence(root),new ArrayList<>()).type());
    }
    @Test void conflictingLoaders() throws Exception {
        TestFixtures.write(root,"manifest.json","{\"minecraft\":{\"modLoaders\":[{\"id\":\"forge-47\"},{\"id\":\"fabric-1\"}]}}");
        final var diagnostics = new ArrayList<ImportDiagnostic>();
        assertEquals(ModLoaderType.UNKNOWN,new ModLoaderDetector().detect(TestFixtures.evidence(root),diagnostics).type());
        assertFalse(diagnostics.isEmpty());
    }
}
