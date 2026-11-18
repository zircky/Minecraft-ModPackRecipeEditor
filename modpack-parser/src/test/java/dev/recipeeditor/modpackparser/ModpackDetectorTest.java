package dev.recipeeditor.modpackparser;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class ModpackDetectorTest {
    @TempDir Path root;
    @Test void ordinaryStructure() throws Exception {
        Files.createDirectories(root.resolve("mods")); Files.createDirectories(root.resolve("config"));
        assertEquals(DetectionResult.Confidence.MEDIUM,new ModpackDetector().detect(root).confidence());
    }
    @ParameterizedTest @ValueSource(strings={".minecraft","minecraft"})
    void multiMcAndPrism(final String game) throws Exception {
        TestFixtures.write(root,"mmc-pack.json","{\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.20.1\"}]}");
        Files.createDirectories(root.resolve(game).resolve("mods"));
        assertEquals(root.resolve(game).toRealPath(),new ModpackDetector().detect(root).gameDirectory());
    }
    @Test void curseForge() throws Exception {
        TestFixtures.write(root,"minecraftinstance.json","{\"gameVersion\":\"1.20.1\"}"); Files.createDirectories(root.resolve("mods"));
        assertEquals("CurseForge",new ModpackDetector().detect(root).structure());
    }
    @Test void rejectsModsOnlyAndEmptyFolder() throws Exception {
        assertFalse(new ModpackDetector().detect(root).recognized());
        Files.createDirectory(root.resolve("mods")); assertFalse(new ModpackDetector().detect(root).recognized());
    }
    @Test void doesNotSearchArbitraryDescendants() throws Exception {
        TestFixtures.write(root,"instances/one/mmc-pack.json","{}");
        assertFalse(new ModpackDetector().detect(root).recognized());
    }
    @Test void malformedMarkerAloneDoesNotProveInstance() throws Exception {
        TestFixtures.write(root,"manifest.json","{}");
        Files.createDirectory(root.resolve("mods"));
        assertFalse(new ModpackDetector().detect(root).recognized());
    }
}
