package dev.recipeeditor.modpackparser;

import java.nio.file.Path;
import java.util.ArrayList;
import dev.recipeeditor.core.importing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class MinecraftVersionDetectorTest {
    @TempDir Path root;
    @Test void detectsVersionAndSource() throws Exception {
        TestFixtures.write(root,"mmc-pack.json","{\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.20.1\"}]}");
        final var value = new MinecraftVersionDetector().detect(TestFixtures.evidence(root),new ArrayList<>());
        assertEquals("1.20.1",value.value()); assertFalse(value.sources().isEmpty());
    }
    @Test void unknownDoesNotGuessFromJava() throws Exception {
        TestFixtures.write(root,"minecraftinstance.json","{\"javaVersion\":\"21\"}");
        assertEquals("UNKNOWN",new MinecraftVersionDetector().detect(TestFixtures.evidence(root),new ArrayList<>()).value());
    }
    @Test void conflictsArePreserved() throws Exception {
        TestFixtures.write(root,"manifest.json","{\"minecraft\":{\"version\":\"1.20.1\"}}");
        TestFixtures.write(root,"minecraftinstance.json","{\"gameVersion\":\"1.21.1\"}");
        final var diagnostics = new ArrayList<ImportDiagnostic>();
        assertEquals("UNKNOWN",new MinecraftVersionDetector().detect(TestFixtures.evidence(root),diagnostics).value());
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().contains("Conflicting")));
    }
}
