package dev.recipeeditor.registry;
import dev.recipeeditor.core.ResourceLocation;
import java.util.Objects;
/** Item and block registries may legally contain different resources with the same ID. */
public record ResourceIdentity(ResourceKind kind,ResourceLocation id) {
    public ResourceIdentity {Objects.requireNonNull(kind);Objects.requireNonNull(id);}
    public static ResourceIdentity of(final RegistryEntry entry){return new ResourceIdentity(entry.kind(),entry.id());}
}
