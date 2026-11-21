package dev.recipeeditor.forge;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Client-only code installs a DTO supplier; dedicated servers never link rendering classes. */
public final class RuntimePresentation {
    private static volatile Supplier<CompletableFuture<Map<String,Map<String,String>>>> collector=()->CompletableFuture.completedFuture(Map.of());
    private RuntimePresentation() { }
    public static void install(final Supplier<CompletableFuture<Map<String,Map<String,String>>>> value) { collector=value; }
    public static CompletableFuture<Map<String,Map<String,String>>> capture() { return collector.get(); }
}
