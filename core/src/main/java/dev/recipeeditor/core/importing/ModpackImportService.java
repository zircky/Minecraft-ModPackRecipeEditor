package dev.recipeeditor.core.importing;

import java.nio.file.Path;
import java.util.function.Consumer;
public interface ModpackImportService extends AutoCloseable {
    ImportOperation start(Path directory, Consumer<ImportProgress> progress);
    @Override void close();
}
