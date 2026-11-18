package dev.recipeeditor.core;

import java.util.Objects;
/** Minecraft identifiers with the conventional minecraft namespace when omitted. */
public record ResourceLocation(String namespace, String path) implements Comparable<ResourceLocation> {
    public ResourceLocation {
        Objects.requireNonNull(namespace); Objects.requireNonNull(path);
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid resource location: " + namespace + ":" + path);
    }
    public static ResourceLocation parse(final String value) {
        Objects.requireNonNull(value);
        final int colon = value.indexOf(':');
        return colon < 0 ? new ResourceLocation("minecraft",value) : new ResourceLocation(value.substring(0,colon),value.substring(colon+1));
    }
    @Override public String toString() { return namespace + ":" + path; }
    @Override public int compareTo(final ResourceLocation other) { return toString().compareTo(other.toString()); }
}
