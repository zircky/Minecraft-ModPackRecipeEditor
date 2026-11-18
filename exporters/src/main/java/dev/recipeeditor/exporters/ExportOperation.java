package dev.recipeeditor.exporters;

import java.util.concurrent.CompletableFuture;
public interface ExportOperation {CompletableFuture<ExportResult> result();boolean cancel();boolean applying();}
