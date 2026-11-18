package dev.recipeeditor.registry;

public record ItemDefinition(RegistryEntry entry) {
    public ItemDefinition { if (entry.kind()!=ResourceKind.ITEM) throw new IllegalArgumentException("Wrong resource kind"); }
}
