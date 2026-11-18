package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ModScannerTest {
    @TempDir Path root;
    private ModScanResult scan(final Path jar) { return new ModScanner().scan(root,jar,new ModScanner.ReadBudget(1024*1024)); }
    @Test void validJarAndMultipleMods() throws Exception {
        final var jar = TestFixtures.jar(root,"multi.jar",Map.of("META-INF/mods.toml",TestFixtures.forge("first")+TestFixtures.forge("second")));
        final var result = scan(jar); assertEquals(2,result.mods().size()); assertEquals(ScanStatus.OK,result.status());
    }
    @Test void corruptJar() throws Exception { assertEquals(ScanStatus.ERROR,scan(TestFixtures.write(root,"bad.jar","bad")).status()); }
    @Test void missingMetadata() throws Exception {
        final var result = scan(TestFixtures.jar(root,"library.jar",Map.of("example.txt","text")));
        assertEquals(ScanStatus.WARNING,result.status()); assertTrue(result.mods().isEmpty());
    }
    @Test void excessiveMetadataRejected() throws Exception {
        assertEquals(ScanStatus.ERROR,scan(TestFixtures.jar(root,"bomb.jar",Map.of("fabric.mod.json"," ".repeat(600000)))).status());
    }
    @Test void noExtractionOfTraversalEntries() throws Exception {
        final var jar = TestFixtures.jar(root,"traversal.jar",Map.of("../escape.txt","bad","fabric.mod.json",TestFixtures.fabric()));
        assertEquals(1,scan(jar).mods().size()); assertFalse(Files.exists(root.resolve("escape.txt")));
    }
    @Test void outsidePathIsRejected() throws Exception {
        final var outside = Files.createTempFile("outside-mod", ".jar");
        try { assertEquals(ScanStatus.ERROR,scan(outside).status()); } finally { Files.delete(outside); }
    }
    @Test void archiveLimitIsEnforced() throws Exception {
        final var scanner = new ModScanner(new ImportLimits(1,100,1,20,100),List.of(new FabricMetadataParser()));
        assertEquals(ScanStatus.ERROR,scanner.scan(root,TestFixtures.jar(root,"small.jar",Map.of("fabric.mod.json",TestFixtures.fabric())),new ModScanner.ReadBudget(100)).status());
    }
    @Test void aggregateBudgetIsConsumedEvenOnReadFailure() throws Exception {
        final var budget = new ModScanner.ReadBudget(10);
        final var result = new ModScanner().scan(root,TestFixtures.jar(root,"budget.jar",Map.of("fabric.mod.json",TestFixtures.fabric())),budget);
        assertEquals(ScanStatus.ERROR,result.status()); assertTrue(budget.exhausted());
    }
    @Test void symbolicLinkOutsideRootIsRejectedWhenSupported() throws Exception {
        final var outside = Files.createTempFile("outside-mod", ".jar");
        try {
            final var link = root.resolve("link.jar");
            try { Files.createSymbolicLink(link,outside); }
            catch (java.io.IOException | UnsupportedOperationException error) {
                org.junit.jupiter.api.Assumptions.abort("OS does not permit creating symbolic links: " + error.getMessage());
            }
            assertEquals(ScanStatus.ERROR,scan(link).status());
        } finally { Files.delete(outside); }
    }
}
