package dev.recipeeditor.recipeengine;

import java.util.concurrent.CompletableFuture;
public interface DiscoveryOperation { CompletableFuture<DiscoveryResult> result(); void cancel(); }
