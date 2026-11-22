package dev.recipeeditor.companionserver;

import dev.recipeeditor.companionprotocol.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
/** Installed by client-only code. Dedicated servers never link Minecraft renderer classes. */
public interface ItemPreviewProvider {
    boolean available();
    String context();
    CompletableFuture<List<ItemPreviewResponse>> capture(List<ItemPreviewRequest> requests,BooleanSupplier cancelled);
    static ItemPreviewProvider unavailable(){return new ItemPreviewProvider(){
        public boolean available(){return false;}public String context(){return "";}
        public CompletableFuture<List<ItemPreviewResponse>> capture(final List<ItemPreviewRequest> requests,final BooleanSupplier cancelled){return CompletableFuture.completedFuture(requests.stream().map(r->ItemPreviewResponse.error(r,"","CLIENT_UNAVAILABLE")).toList());}
    };}
}
