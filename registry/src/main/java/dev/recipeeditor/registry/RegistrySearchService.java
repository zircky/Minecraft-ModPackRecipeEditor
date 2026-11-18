package dev.recipeeditor.registry;

import java.util.*;
public final class RegistrySearchService {
    private final RegistryIndex index;
    public RegistrySearchService(final RegistryIndex index) { this.index=Objects.requireNonNull(index); }
    public List<RegistryEntry> search(final ResourceKind kind, final String query, final String modId) {
        final String text=query.toLowerCase(Locale.ROOT).strip();
        return index.entries(kind).stream().filter(e -> modId.isEmpty() || e.sources().stream().anyMatch(s -> s.modIds().contains(modId)))
                .filter(e -> e.id().toString().contains(text) || e.displayName().toLowerCase(Locale.ROOT).contains(text)
                        || e.localizedNames().values().stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).contains(text))).toList();
    }
}
