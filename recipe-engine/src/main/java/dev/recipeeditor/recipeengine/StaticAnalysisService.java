package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.importing.*;
import java.util.function.Consumer;
public interface StaticAnalysisService extends AutoCloseable {
    DiscoveryOperation start(ModpackDefinition modpack,Consumer<ImportProgress> progress);
    @Override void close();
}
