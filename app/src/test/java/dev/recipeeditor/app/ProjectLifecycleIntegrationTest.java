package dev.recipeeditor.app;

import static org.junit.jupiter.api.Assertions.*;

import dev.recipeeditor.app.project.FileProjectPersistence;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.exporters.*;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.ResourceKind;
import dev.recipeeditor.testing.SyntheticModpack;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("integration")
final class ProjectLifecycleIntegrationTest {
    @TempDir Path root;

    @ParameterizedTest
    @EnumSource(value = ExportFormat.class, names = {"DATAPACK", "KUBEJS", "CRAFTTWEAKER"})
    void importEditReopenAnalyzeAndExportWithoutChangingJar(final ExportFormat format) throws Exception {
        final var fixture = new SyntheticModpack(root.resolve("Сборка с пробелами"));
        final byte[] originalJar = Files.readAllBytes(fixture.jar());
        final var id = ResourceLocation.parse("fixture:smelt");
        final var deleted = ResourceLocation.parse("fixture:plate");
        final var copy = ResourceLocation.parse("crafttweaker:phase9_copy");
        final var opaqueCopy = ResourceLocation.parse("fixture:opaque_copy");
        final var project = root.resolve("Проект с пробелами.json");
        try (final var importer = new DefaultModpackImportService();
             final var discoveryService = new DefaultStaticAnalysisService();
             final var storage = new FileProjectPersistence()) {
            final var imported = importer.start(fixture.directory(), p -> {}).result().get(20, TimeUnit.SECONDS);
            assertEquals("1.20.1", imported.modpack().minecraftVersion().value());
            assertEquals(3, imported.modpack().mods().size());
            final var discovery = discoveryService.start(imported.modpack(), p -> {}).result().get(20, TimeUnit.SECONDS);
            assertEquals(3, discovery.recipes().recipes().size());
            assertTrue(discovery.registry().entries(ResourceKind.ITEM).size() >= 3);
            final var validator = validator(discovery);
            final var editor = new RecipeEditorService(discovery.recipes(), validator);
            final var original = discovery.recipes().findById(id).getFirst();
            editor.update(id, validator.validate(id, fixture.smelting(300), original.source()).recipe());
            editor.delete(deleted);
            editor.undo();
            assertEquals(1, editor.effective().findById(deleted).size());
            editor.redo();
            editor.duplicate(editor.effective().findById(id).getFirst(), copy);
            editor.duplicate(editor.effective().findById(ResourceLocation.parse("fixture:opaque")).getFirst(), opaqueCopy);
            final var captured = editor.persistentSnapshot();
            storage.save(project, ProjectDocument.capture(fixture.directory(), captured)).get(10, TimeUnit.SECONDS);
            editor.markPersisted(captured);
            assertFalse(editor.dirty());
            editor.duplicate(editor.effective().findById(copy).getFirst(), ResourceLocation.parse("fixture:unsaved"));

            // Reopen includes another real scan, not reuse of the previous in-memory index.
            final var reimported = importer.start(fixture.directory(), p -> {}).result().get(20, TimeUnit.SECONDS);
            final var reopenedDiscovery = discoveryService.start(reimported.modpack(), p -> {}).result().get(20, TimeUnit.SECONDS);
            final var reopenedValidator = validator(reopenedDiscovery);
            final var reopened = new RecipeEditorService(reopenedDiscovery.recipes(), reopenedValidator);
            final var document = storage.load(project).get(10, TimeUnit.SECONDS);
            reopened.restore(document.materialize(reopenedValidator, project));
            assertEquals(document.changes(), ProjectDocument.capture(fixture.directory(), reopened.persistentSnapshot()).changes(),
                    "Persistent ChangeSet preserves original references, timestamps and unknown raw JSON");
            assertFalse(reopened.dirty());
            assertTrue(reopened.conflicts().isEmpty());
            assertTrue(reopened.effective().findById(deleted).isEmpty());
            assertTrue(reopened.effective().findById(ResourceLocation.parse("fixture:unsaved")).isEmpty());
            assertEquals(fixture.opaqueJson(), reopened.effective().findById(opaqueCopy).getFirst().rawJson());
            assertEquals(200, new dev.recipeeditor.registry.ResourceJsonReader().read(original.rawJson()).path("cookingtime").asInt());

            final var context = AnalysisContext.staticProject("1.20.1", "FORGE", Map.of(),
                    reopened.originals(), reopenedDiscovery.registry(), reopened.snapshot());
            final var analyzers = new DefaultAnalyzers().registry();
            final var results = analyzers.analyze(context, Set.copyOf(analyzers.ids()));
            assertEquals(3, results.size());
            assertFalse(context.dependencyGraph().truncated());
            final var affected = new ReverseDependencyIndex().dependents(context.dependencyGraph(),
                    new NodeKey(DependencyNodeType.ITEM, "minecraft-item", "fixture:ore"), 16, 1000, () -> false);
            assertFalse(affected.truncated());
            assertEquals(2, affected.recipes().size());

            final var target = Files.createDirectories(root.resolve("Export " + format));
            final var profile = new ExportProfile("phase9test", "Synthetic lifecycle", target, format,
                    OverwritePolicy.OWNED_UNCHANGED, true, "1.20.1", "FORGE");
            final var exportContext = new ExportContext(reimported.modpack(), reopenedDiscovery.recipes(),
                    reopened.snapshot(), project, profile, List.of(), Set.of(), false);
            final var planner = planner(reopenedValidator);
            // Opaque data must survive storage, but no serializer may silently export it as supported.
            assertFalse(planner.plan(new ExportRequest(exportContext, Set.of(opaqueCopy), Map.of(), List.of())).valid());
            if (format == ExportFormat.DATAPACK) {
                assertFalse(planner.plan(new ExportRequest(exportContext, Set.of(deleted), Map.of(), List.of())).valid(),
                        "Ordinary datapacks cannot universally delete recipes");
            }
            final Set<ResourceLocation> selection = switch (format) {
                case DATAPACK -> Set.of(id, copy);
                case CRAFTTWEAKER -> Set.of(copy);
                default -> Set.of(id, deleted, copy);
            };
            try (final var exports = new RecipeExportService(planner)) {
                final var plan = exports.dryRun(new ExportRequest(exportContext, selection, Map.of(), List.of())).get(10, TimeUnit.SECONDS);
                assertTrue(plan.valid(), plan.diagnostics().toString());
                assertFalse(plan.files().isEmpty());
                assertEquals(selection, plan.recipes().stream().map(r -> r.change().id()).collect(java.util.stream.Collectors.toSet()));
                assertTrue(plan.files().values().stream().anyMatch(text -> text.contains("300")),
                        "Export must contain the saved changed cooking time");
                try (final var files = Files.list(target)) { assertEquals(0, files.count(), "Dry run writes nothing"); }
                final var result = exports.export(plan, p -> {}).result().get(10, TimeUnit.SECONDS);
                assertEquals(ExportResult.Status.SUCCESS, result.status(), result.diagnostics().toString());
                assertEquals("GENERATED_RUNTIME_UNVERIFIED", result.manifests().getFirst().status());
                for (final var output : plan.files().entrySet()) {
                    assertEquals(output.getValue(), Files.readString(target.resolve(output.getKey())));
                }
                assertEquals(1, exports.workspace(project).get(10, TimeUnit.SECONDS).manifests().size());
            }
        }
        assertArrayEquals(originalJar, Files.readAllBytes(fixture.jar()), "All source JAR bytes stay unchanged");
    }

    @Test
    void changedInstalledSourceQuarantinesSavedEditAndRetainsItOnResave() throws Exception {
        final var fixture = new SyntheticModpack(root.resolve("instance"));
        final var project = root.resolve("project.json");
        final var id = ResourceLocation.parse("fixture:smelt");
        try (final var importer = new DefaultModpackImportService();
             final var analyzer = new DefaultStaticAnalysisService();
             final var storage = new FileProjectPersistence()) {
            final var pack = importer.start(fixture.directory(), p -> {}).result().get(20, TimeUnit.SECONDS).modpack();
            final var discovery = analyzer.start(pack, p -> {}).result().get(20, TimeUnit.SECONDS);
            final var editor = new RecipeEditorService(discovery.recipes(), validator(discovery));
            final var original = discovery.recipes().findById(id).getFirst();
            editor.update(id, validator(discovery).validate(id, fixture.smelting(300), original.source()).recipe());
            storage.write(project, ProjectDocument.capture(fixture.directory(), editor.snapshot()));
            final var before = storage.read(project).changes();
            fixture.changeInstalledRecipe(400);
            final var changedJar = Files.readAllBytes(fixture.jar());
            final var currentPack = importer.start(fixture.directory(), p -> {}).result().get(20, TimeUnit.SECONDS).modpack();
            final var current = analyzer.start(currentPack, p -> {}).result().get(20, TimeUnit.SECONDS);
            final var restored = new RecipeEditorService(current.recipes(), validator(current));
            restored.restore(storage.read(project).materialize(validator(current), project));
            assertTrue(restored.hasConflict(id));
            assertTrue(restored.snapshot().changes().isEmpty(), "Quarantined change is not an active edit");
            assertEquals(fixture.smelting(400), restored.effective().findById(id).getFirst().rawJson());
            storage.write(project, ProjectDocument.capture(fixture.directory(), restored.persistentSnapshot()));
            assertEquals(before, storage.read(project).changes(), "Conflict must not silently erase a saved edit");
            assertArrayEquals(changedJar, Files.readAllBytes(fixture.jar()));
        }
    }

    private RecipeValidator validator(final DiscoveryResult discovery) {
        return new RecipeValidator(new ProjectRecipeParsers().parsers(), discovery.registry());
    }

    private ExportPlanner planner(final RecipeValidator validator) {
        return new ExportPlanner(List.of(new DatapackRecipeExporter(), new KubeJsRecipeExporter(),
                new CraftTweakerRecipeExporter()), new ExportValidator(validator));
    }
}
