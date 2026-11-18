package dev.recipeeditor.core;

import java.util.Objects;

/** Immutable application identity, independent of the desktop toolkit. */
public record ApplicationInfo(String name, String version) {
    public ApplicationInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (name.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("Application name and version must not be blank");
        }
    }
}
