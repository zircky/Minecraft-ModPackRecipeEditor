package dev.recipeeditor.app;

import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.exporters.*;
import java.util.List;
import dev.recipeeditor.registry.RegistryIndex;
import dev.recipeeditor.ui.MainWindow;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import javafx.application.Application;
import dev.recipeeditor.app.project.FileProjectPersistence;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.TextureResourceService;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecipeEditorApplication extends Application {
    private final DefaultModpackImportService importService = new DefaultModpackImportService();
    private final DefaultStaticAnalysisService analysisService = new DefaultStaticAnalysisService();
    private final TextureResourceService textures = new TextureResourceService();
    private final FileProjectPersistence persistence = new FileProjectPersistence();
    private final RecipeExportService exports = new RecipeExportService(new ExportPlanner(List.of(new DatapackRecipeExporter(),new KubeJsRecipeExporter(),new CraftTweakerRecipeExporter()), new ExportValidator(new RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),new RegistryIndex(List.of())))));
    private final CompanionConnectionService companion = new CompanionConnectionService();
    private final RuntimeSnapshotService runtimeSnapshots = new RuntimeSnapshotService();
    private final Logger logger = LoggerFactory.getLogger(RecipeEditorApplication.class);

    @Override
    public void start(final Stage stage) {
        final var metadata = dev.recipeeditor.app.release.BuildMetadata.load();
        final var info = metadata.applicationInfo();
        dev.recipeeditor.ui.rendering.InventoryPreviewProvider.shared().client(companion);
        new MainWindow(info, importService, analysisService, textures, persistence,
                (result, format) -> new RecipeEditorService(result.recipes(),
                        new RecipeValidator(format.parsers(), result.registry(), format.layout())),
                exports, path -> getHostServices().showDocument(path.toUri().toString()), companion, runtimeSnapshots).show(stage);
        new dev.recipeeditor.app.release.ReleaseInformationView(metadata,
                path -> getHostServices().showDocument(path.toUri().toString())).attach(stage);
        logger.info("Started {} {}", info.name(), info.version());
        logger.info("Build protocol={}, companion={}, projectFormat={}, releaseStatus={}", metadata.protocolVersion(),
                metadata.companionVersion(), metadata.projectFormatVersion(), metadata.releaseStatus());
    }

    @Override
    public void stop() {
        dev.recipeeditor.ui.rendering.InventoryPreviewProvider.shared().close();
        companion.close();
        exports.close();
        importService.close();
        analysisService.close();
        textures.close();
        persistence.close();
        logger.info("Application stopped");
    }
}
