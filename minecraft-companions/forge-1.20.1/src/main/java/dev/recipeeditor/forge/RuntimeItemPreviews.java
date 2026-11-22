package dev.recipeeditor.forge;

import dev.recipeeditor.companionserver.ItemPreviewProvider;
import dev.recipeeditor.companionprotocol.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
/** No client class reference in this dedicated-server-safe bridge. */
public final class RuntimeItemPreviews {
    private static volatile ItemPreviewProvider delegate=ItemPreviewProvider.unavailable();
    private RuntimeItemPreviews(){}
    public static void install(final ItemPreviewProvider provider){delegate=Objects.requireNonNull(provider);}
    public static ItemPreviewProvider bridge(){return new ItemPreviewProvider(){
        public boolean available(){return delegate.available();}public String context(){return delegate.context();}
        public CompletableFuture<List<ItemPreviewResponse>> capture(final List<ItemPreviewRequest> items,final BooleanSupplier cancelled){return delegate.capture(items,cancelled);}
    };}
}
