package dev.recipeeditor.app;

import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("integration")
final class StaticAnalysisServiceTest {
    @TempDir Path root;
    private ModpackDefinition pack(final String version) throws Exception {
        final var jar=root.resolve("example.jar");
        try(final var zip=new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("assets/test/models/item/a.json")); zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("data/test/recipes/example.json"));
            zip.write("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"test:ore\"},\"result\":\"test:a\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return new ModpackDefinition(root,root,"test",new MinecraftVersion(version,List.of()),new ModLoaderDefinition(ModLoaderType.FORGE,"47",List.of()),List.of(new ModScanResult(jar,List.of(),ScanStatus.OK,List.of())));
    }
    @Test void discoversResourcesAndRecipesAsync() throws Exception {
        final var stages=new CopyOnWriteArrayList<String>();
        try(final var service=new DefaultStaticAnalysisService()) {
            final var result=service.start(pack("1.20.1"),p->stages.add(p.stage())).result().get(10,TimeUnit.SECONDS);
            assertEquals(1,result.registry().entries(ResourceKind.ITEM).size()); assertEquals(1,result.recipes().recipes().size());
            assertTrue(stages.contains("Indexing items...")); assertEquals("Completed.",stages.getLast());
        }
    }
    @Test void unsupportedVersionIsExplicitAndNotGuessed() throws Exception {
        try(final var service=new DefaultStaticAnalysisService()) {
            final var result=service.start(pack("UNKNOWN"),p->{}).result().get(5,TimeUnit.SECONDS);
            assertTrue(result.registry().entries().isEmpty()); assertFalse(result.diagnostics().isEmpty());
        }
    }
    @Test void cancellationReleasesWorkerAndAllowsRetry() throws Exception {
        final var entered=new CountDownLatch(1); final var interrupted=new CountDownLatch(1); final var pack=pack("1.20.1");
        try(final var service=new DefaultStaticAnalysisService()) {
            final var operation=service.start(pack,p->{
                entered.countDown(); try { new CountDownLatch(1).await(); }
                catch(InterruptedException error) { Thread.currentThread().interrupt(); interrupted.countDown(); }
            });
            assertTrue(entered.await(3,TimeUnit.SECONDS)); operation.cancel(); assertTrue(interrupted.await(3,TimeUnit.SECONDS)); assertTrue(operation.result().isCancelled());
            assertEquals(1,service.start(pack,p->{}).result().get(5,TimeUnit.SECONDS).recipes().recipes().size());
        }
        Files.move(root.resolve("example.jar"),root.resolve("closed.jar"));
    }
}
