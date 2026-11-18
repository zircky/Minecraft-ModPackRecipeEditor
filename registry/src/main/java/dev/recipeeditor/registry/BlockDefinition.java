package dev.recipeeditor.registry;

public record BlockDefinition(RegistryEntry entry) {
    public BlockDefinition { if (entry.kind()!=ResourceKind.BLOCK) throw new IllegalArgumentException("Wrong resource kind"); }
}
