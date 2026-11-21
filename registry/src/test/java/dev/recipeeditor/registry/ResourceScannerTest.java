package dev.recipeeditor.registry;

import dev.recipeeditor.core.importing.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class ResourceScannerTest {
    @TempDir Path root;
    private ResourceScanResult scan(final Path jar) { return new ResourceScanner(new DiscoveryLimits(),ResourceLayout.minecraft1201()).scan(ResourceFixtures.pack(root,jar),p->{}); }
    @Test void detectsResourcesAndRetainsSources() throws Exception {
        final var result=scan(ResourceFixtures.jar(root,Map.of("assets/test/models/item/a.json","{}","data/test/recipes/a.json","{}","assets/test/textures/item/a.png","not-decoded")));
        assertEquals(2,result.documents().size()); assertEquals(1,result.textures().size());
        assertEquals(SourceType.MOD_JAR,result.documents().getFirst().source().type());
    }
    @Test void missingResourcesAreFine() throws Exception { assertTrue(scan(ResourceFixtures.jar(root,Map.of("test.txt","text"))).documents().isEmpty()); }
    @Test void corruptArchiveBecomesDiagnostic() throws Exception { final var jar=root.resolve("bad.jar"); Files.writeString(jar,"bad"); assertFalse(scan(jar).diagnostics().isEmpty()); }
    @Test void traversalRejectedWithoutExtraction() throws Exception {
        final var result=scan(ResourceFixtures.jar(root,Map.of("assets/test/models/item/../../escape.json","{}","/absolute.json","{}")));
        assertTrue(result.documents().isEmpty()); assertEquals(2,result.diagnostics().stream().filter(d->d.message().contains("Unsafe resource path")).count()); assertFalse(Files.exists(root.resolve("escape.json")));
    }
    @Test void readsFolderAndZipDatapacksAndLocalData() throws Exception {
        final var jar=ResourceFixtures.jar(root,Map.of());
        final var data=root.resolve("datapacks/example/data/test/recipes/local.json"); Files.createDirectories(data.getParent()); Files.writeString(data,"{}");
        final var kube=root.resolve("kubejs/data/test/recipes/scriptdata.json"); Files.createDirectories(kube.getParent()); Files.writeString(kube,"{}");
        try(final var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(root.resolve("datapacks/archive.zip")))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("data/test/recipes/zipped.json")); zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        final var result=scan(jar); assertEquals(3,result.documents().size());
        assertTrue(result.documents().stream().anyMatch(d->d.source().type()==SourceType.DATAPACK));
        assertTrue(result.documents().stream().anyMatch(d->d.source().type()==SourceType.LOCAL_RESOURCE));
    }
    @Test void sizeAndAggregateLimitsAreReported() throws Exception {
        final var jar=ResourceFixtures.jar(root,Map.of("data/test/recipes/a.json","x".repeat(100)));
        final var scanner=new ResourceScanner(new DiscoveryLimits(10,20,50,100,100000),ResourceLayout.minecraft1201());
        assertFalse(scanner.scan(ResourceFixtures.pack(root,jar),p->{}).diagnostics().isEmpty());
    }
}
