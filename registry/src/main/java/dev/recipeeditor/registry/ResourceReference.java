package dev.recipeeditor.registry;
import dev.recipeeditor.core.ResourceLocation;
import java.util.Objects;
/** Independent identity evidence, e.g. a parsed recipe operand; never proof of runtime registration. */
public record ResourceReference(ResourceKind kind,ResourceLocation id,ResourceSource source) {
    public ResourceReference {Objects.requireNonNull(kind);Objects.requireNonNull(id);Objects.requireNonNull(source);}
}
