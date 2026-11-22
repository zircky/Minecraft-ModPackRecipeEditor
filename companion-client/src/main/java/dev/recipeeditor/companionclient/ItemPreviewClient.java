package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
public interface ItemPreviewClient {
    boolean itemPreviewsAvailable();
    default String previewUnavailableReason(){return itemPreviewsAvailable()?"CLIENT_CONNECTED; client item preview available":"CLIENT_RENDERER_UNAVAILABLE";}
    String previewContext();
    CompletableFuture<List<ItemPreviewResponse>> itemPreviews(List<ItemPreviewRequest> requests);
}
