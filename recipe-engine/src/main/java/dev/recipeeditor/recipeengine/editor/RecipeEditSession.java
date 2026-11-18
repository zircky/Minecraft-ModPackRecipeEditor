package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.util.*;
/** Independent draft. Validation results belong to an exact revision, never to stale JSON. */
public final class RecipeEditSession {
    public enum Mode { VISUAL, ADVANCED, RAW_JSON, DIFF }
    private final ResourceLocation id;
    private final RecipeSource source;
    private final String originalJson;
    private String rawJson,savedJson;
    private long revision;
    private Mode mode=Mode.VISUAL;
    public RecipeEditSession(final ResourceLocation id,final RecipeSource source,final String raw) {
        this(id,source,raw,raw);
    }
    public RecipeEditSession(final ResourceLocation id,final RecipeSource source,final String raw,final String original) {
        this.id=Objects.requireNonNull(id); this.source=Objects.requireNonNull(source); originalJson=Objects.requireNonNull(original); rawJson=Objects.requireNonNull(raw); savedJson=raw;
    }
    public synchronized String rawJson() { return rawJson; }
    public ResourceLocation id() { return id; }
    public RecipeSource source() { return source; }
    public String originalJson() { return originalJson; }
    public synchronized long revision() { return revision; }
    public synchronized void setRawJson(final String value) { Objects.requireNonNull(value); if(value.length()>2*1024*1024) throw new IllegalArgumentException("JSON exceeds editor limit"); rawJson=value; revision++; }
    public synchronized boolean dirty() { return !rawJson.equals(savedJson); }
    public synchronized void markSaved(final String saved) { savedJson=saved; }
    public synchronized void cancel() { rawJson=savedJson; revision++; }
    public synchronized void mode(final Mode mode) { this.mode=Objects.requireNonNull(mode); }
    public synchronized Mode mode() { return mode; }
}
