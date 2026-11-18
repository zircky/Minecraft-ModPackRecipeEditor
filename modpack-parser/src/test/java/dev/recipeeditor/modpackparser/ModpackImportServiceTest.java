package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ModpackImportServiceTest {
    @TempDir Path root;
    @Test void importsAndContinuesAfterCorruptJar() throws Exception {
        TestFixtures.write(root,"manifest.json","{\"minecraft\":{\"version\":\"1.20.1\",\"modLoaders\":[{\"id\":\"forge-47.4.0\"}]}}");
        TestFixtures.jar(root,"mods/valid.jar",Map.of("META-INF/mods.toml",TestFixtures.forge("sample")));
        TestFixtures.write(root,"mods/bad.jar","broken");
        final var progress = new CopyOnWriteArrayList<ImportProgress>();
        try (final var service = new DefaultModpackImportService()) {
            final var result = service.start(root,progress::add).result().get(10,TimeUnit.SECONDS);
            assertEquals(2,result.modpack().jars().size()); assertEquals(1,result.modpack().mods().size());
            assertEquals("1.20.1",result.modpack().minecraftVersion().value());
            assertEquals(1,progress.getLast().fraction());
        }
        // Reopening writable verifies archive resources were released on Windows.
        Files.move(root.resolve("mods/valid.jar"),root.resolve("mods/moved.jar"));
    }
    @Test void cancellationInterruptsWorkerAndAllowsAnotherImport() throws Exception {
        TestFixtures.write(root,"manifest.json","{\"minecraft\":{\"version\":\"1.20.1\"}}");
        final var entered = new CountDownLatch(1);
        final var interrupted = new CountDownLatch(1);
        try (final var service = new DefaultModpackImportService()) {
            final var operation = service.start(root,progress -> {
                entered.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); interrupted.countDown(); }
            });
            assertTrue(entered.await(3,TimeUnit.SECONDS)); operation.cancel();
            assertTrue(interrupted.await(3,TimeUnit.SECONDS)); assertTrue(operation.result().isCancelled());
            assertNotNull(service.start(root,p -> {}).result().get(5,TimeUnit.SECONDS));
        }
    }
    @Test void invalidDirectoryReportsError() throws Exception {
        try (final var service = new DefaultModpackImportService()) {
            assertThrows(ExecutionException.class,() -> service.start(root,p -> {}).result().get(5,TimeUnit.SECONDS));
        }
    }
    @Test void fileCountLimit() throws Exception {
        TestFixtures.write(root,"manifest.json","{\"minecraft\":{\"version\":\"1.20.1\"}}");
        TestFixtures.jar(root,"mods/one.jar",Map.of()); TestFixtures.jar(root,"mods/two.jar",Map.of());
        try (final var service = new DefaultModpackImportService(new ImportLimits(1,1000,100,100,1000))) {
            assertThrows(ExecutionException.class,() -> service.start(root,p -> {}).result().get(5,TimeUnit.SECONDS));
        }
    }
}
