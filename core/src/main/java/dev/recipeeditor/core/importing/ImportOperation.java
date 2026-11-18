package dev.recipeeditor.core.importing;

import java.util.concurrent.CompletableFuture;
public interface ImportOperation {
    CompletableFuture<ModpackImportResult> result();
    void cancel();
}
