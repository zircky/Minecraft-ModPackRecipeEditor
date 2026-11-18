package dev.recipeeditor.integrations.gregtech;
import com.fasterxml.jackson.databind.node.*;
import dev.recipeeditor.integrations.api.RecipeEditing;
/** Patches only existing leaves; does not synthesize missing parameters or discard unknown siblings. */
public final class GregTechRecipeEditing implements RecipeEditing {
    @Override public String replace(final String raw, final String pointer, final String jsonValue) {
        final var mapper = new GregTechRecipeMapper(); final var root = mapper.object(raw).deepCopy();
        if (!pointer.startsWith("/") || pointer.equals("/type")) throw new IllegalArgumentException("Existing non-type property required");
        final int split = pointer.lastIndexOf('/'); final var parent = root.at(pointer.substring(0, split));
        final String key = pointer.substring(split + 1).replace("~1", "/").replace("~0", "~");
        if (root.at(pointer).isMissingNode()) throw new IllegalArgumentException("Property is unavailable");
        try {
            final var value = new dev.recipeeditor.registry.ResourceJsonReader().read(jsonValue);
            if (parent instanceof ObjectNode object) object.set(key, value);
            else if (parent instanceof ArrayNode array) array.set(Integer.parseInt(key), value);
            else throw new IllegalArgumentException("Not an editable property");
        } catch (java.io.IOException error) { throw new IllegalArgumentException(error.getMessage(), error); }
        return root.toPrettyString();
    }
}
