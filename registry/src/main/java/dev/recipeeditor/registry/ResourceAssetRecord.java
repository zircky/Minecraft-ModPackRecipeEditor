package dev.recipeeditor.registry;
import dev.recipeeditor.core.ResourceLocation;
import java.util.Objects;
/** An asset definition is not a registered resource. All physical definitions remain available. */
public record ResourceAssetRecord(Kind kind,ResourceLocation id,ResourceSource source) {
    public enum Kind { ITEM_MODEL, BLOCK_MODEL, OTHER_MODEL, BLOCKSTATE, TEXTURE, ATLAS }
    public ResourceAssetRecord {Objects.requireNonNull(kind);Objects.requireNonNull(id);Objects.requireNonNull(source);}
}
