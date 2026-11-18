package dev.recipeeditor.companionserver;
import dev.recipeeditor.companionprotocol.RuntimeData;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
public interface SnapshotProvider {
    CompletableFuture<RuntimeData.Snapshot> capture(String sessionId, BooleanSupplier cancelled, Consumer<String> progress);
    long generation();
    boolean ready();
}
