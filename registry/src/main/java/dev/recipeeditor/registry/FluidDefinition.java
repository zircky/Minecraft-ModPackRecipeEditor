package dev.recipeeditor.registry;

public record FluidDefinition(RegistryEntry entry) {
    public FluidDefinition { if (entry.kind()!=ResourceKind.FLUID) throw new IllegalArgumentException("Wrong resource kind"); }
}
