package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
public interface ItemPreviewClient {
    boolean itemPreviewsAvailable();
    String previewContext();
    CompletableFuture<List<ItemPreviewResponse>> itemPreviews(List<ItemPreviewRequest> requests);
}
