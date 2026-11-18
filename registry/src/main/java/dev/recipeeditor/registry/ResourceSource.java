package dev.recipeeditor.registry;

import java.nio.file.Path;
import java.util.*;
public record ResourceSource(SourceType type, List<String> modIds, Path container, String internalPath, String method) {
    public ResourceSource { Objects.requireNonNull(type); modIds=List.copyOf(modIds); Objects.requireNonNull(container); Objects.requireNonNull(internalPath); Objects.requireNonNull(method); }
}
