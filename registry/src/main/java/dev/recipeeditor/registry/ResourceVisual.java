package dev.recipeeditor.registry;

import java.util.List;
import java.util.Objects;

/** A bounded preview description, never a claim to render Minecraft geometry. */
public record ResourceVisual(Status status, List<Layer> layers, String explanation) {
    public enum Status {
        READY, TEXTURE_SAMPLE, TEXTURE_NOT_FOUND, RUNTIME_TEXTURE_REQUIRED,
        UNSUPPORTED_RENDERER, RENDERING_UNSUPPORTED, INVALID_TEXTURE, UNKNOWN_RESOURCE, AMBIGUOUS_SOURCE
    }
    public record Layer(ResourceSource source, Integer argbTint, String expectedSha256) {
        public Layer(final ResourceSource source,final Integer argbTint) { this(source,argbTint,""); }
        public Layer { Objects.requireNonNull(source);Objects.requireNonNull(expectedSha256);if(!expectedSha256.isEmpty()&&!expectedSha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid active texture hash"); }
    }
    public ResourceVisual {
        Objects.requireNonNull(status); layers=List.copyOf(layers); Objects.requireNonNull(explanation);
        if (layers.size()>8) throw new IllegalArgumentException("Preview layer limit exceeded");
    }
    public static ResourceVisual unavailable(final Status status, final String reason) {
        return new ResourceVisual(status,List.of(),reason);
    }
    public static ResourceVisual legacy(final List<ResourceSource> textures) {
        return textures.size()==1 ? new ResourceVisual(Status.TEXTURE_SAMPLE,List.of(new Layer(textures.getFirst(),null)),"Texture sample")
                : unavailable(Status.RUNTIME_TEXTURE_REQUIRED,"No unambiguous static preview; runtime metadata may be required");
    }
}
