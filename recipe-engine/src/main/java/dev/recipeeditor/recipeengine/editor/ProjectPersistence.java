package dev.recipeeditor.recipeengine.editor;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
public interface ProjectPersistence extends AutoCloseable {
    CompletableFuture<ProjectDocument> load(Path file);
    CompletableFuture<Void> save(Path file,ProjectDocument project);
    @Override void close();
}
