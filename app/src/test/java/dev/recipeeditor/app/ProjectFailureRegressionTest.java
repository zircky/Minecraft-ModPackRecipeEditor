package dev.recipeeditor.app;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.app.project.FileProjectPersistence;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.exporters.*;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.testing.SyntheticModpack;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
final class ProjectFailureRegressionTest {
    @TempDir Path root;

    @Test
    void corruptPrimaryIsNotOverwrittenAndBackupRequiresExplicitSelection() throws Exception {
        final var fixture = new SyntheticModpack(root.resolve("instance"));
        final var project = root.resolve("project.json");
        try (final var storage = new FileProjectPersistence()) {
            final var document = ProjectDocument.capture(fixture.directory(), new RecipeChangeSet());
            storage.save(project, document).get(10, TimeUnit.SECONDS);
            storage.save(project, document).get(10, TimeUnit.SECONDS);
            final var backup = project.resolveSibling("project.json.bak");
            final byte[] goodBackup = Files.readAllBytes(backup);
            Files.writeString(project, "{\"interrupted\":");
            final byte[] brokenPrimary = Files.readAllBytes(project);
            assertThrows(IOException.class, () -> storage.write(project, document));
            assertArrayEquals(brokenPrimary, Files.readAllBytes(project));
            assertArrayEquals(goodBackup, Files.readAllBytes(backup));
            assertEquals(document, storage.load(backup).get(10, TimeUnit.SECONDS));
            assertArrayEquals(brokenPrimary, Files.readAllBytes(project), "Reading backup never rewrites the current project");
            try (final var files = Files.list(root)) {
                assertTrue(files.noneMatch(p -> p.getFileName().toString().startsWith(".recipe-project-")),
                        "Failed save must clean its temporary staging file");
            }
        }
    }

    @Test
    void staleExportPreviewCannotOverwriteUserDataOrCommitManifest() throws Exception {
        final var fixture = new SyntheticModpack(root.resolve("instance"));
        final byte[] sourceJar = Files.readAllBytes(fixture.jar());
        final var project = root.resolve("project.json");
        try (final var importer = new DefaultModpackImportService();
             final var discoveryService = new DefaultStaticAnalysisService();
             final var storage = new FileProjectPersistence()) {
            final var pack = importer.start(fixture.directory(), p -> {}).result().get(20, TimeUnit.SECONDS).modpack();
            final var discovery = discoveryService.start(pack, p -> {}).result().get(20, TimeUnit.SECONDS);
            final var validator = new RecipeValidator(new ProjectRecipeParsers().parsers(), discovery.registry());
            final var editor = new RecipeEditorService(discovery.recipes(), validator);
            editor.duplicate(discovery.recipes().findById(ResourceLocation.parse("fixture:smelt")).getFirst(),
                    ResourceLocation.parse("fixture:export_copy"));
            storage.write(project, ProjectDocument.capture(fixture.directory(), editor.snapshot()));
            final var target = Files.createDirectories(root.resolve("target"));
            final var profile = new ExportProfile("phase9test", "Failure regression", target, ExportFormat.DATAPACK,
                    OverwritePolicy.REFUSE, true, "1.20.1", "FORGE");
            final var context = new ExportContext(pack, discovery.recipes(), editor.snapshot(), project,
                    profile, List.of(), Set.of(), false);
            final var planner = new ExportPlanner(List.of(new DatapackRecipeExporter()), new ExportValidator(validator));
            try (final var exports = new RecipeExportService(planner)) {
                final var preview = exports.dryRun(new ExportRequest(context, editor.snapshot().changes().keySet(),
                        Map.of(), List.of())).get(10, TimeUnit.SECONDS);
                assertTrue(preview.valid(), preview.diagnostics().toString());
                final var file = target.resolve(preview.files().keySet().iterator().next());
                Files.createDirectories(file.getParent());
                Files.writeString(file, "user file created after preview");
                final var result = exports.export(preview, p -> {}).result().get(10, TimeUnit.SECONDS);
                assertEquals(ExportResult.Status.FAILED, result.status());
                assertFalse(result.diagnostics().isEmpty());
                assertTrue(result.manifests().isEmpty());
                assertTrue(exports.workspace(project).get(10, TimeUnit.SECONDS).manifests().isEmpty());
                assertEquals("user file created after preview", Files.readString(file));
                for (final var path : preview.files().keySet()) {
                    if (!target.resolve(path).equals(file)) assertFalse(Files.exists(target.resolve(path)));
                }
            }
        }
        assertArrayEquals(sourceJar, Files.readAllBytes(fixture.jar()));
    }
}
