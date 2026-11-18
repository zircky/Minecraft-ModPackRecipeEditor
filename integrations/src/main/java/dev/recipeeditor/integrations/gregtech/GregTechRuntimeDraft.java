package dev.recipeeditor.integrations.gregtech;
import com.fasterxml.jackson.databind.node.*;
import dev.recipeeditor.companionprotocol.RuntimeData;
/** Explicit new-ID copy of serializable loaded state, never an original-source reconstruction. */
public final class GregTechRuntimeDraft {
    public String copy(final RuntimeData.Recipe recipe) {
        new GregTechRecipeMapper().map(recipe);
        final var root = (ObjectNode)new GregTechRecipeMapper().object(recipe.properties().toString()).deepCopy();
        if (root.has("runtimeVariants")) throw new IllegalArgumentException("Ambiguous runtime variants: choose an unambiguous source");
        for (final String key : java.util.List.of("sourceJsonAvailable", "recipeCategory", "inputEUt", "outputEUt")) root.remove(key);
        for (final String role : java.util.List.of("inputs", "outputs", "tickInputs", "tickOutputs"))
            root.path(role).forEach(values -> values.forEach(value -> { if (value instanceof ObjectNode content) content.remove("nonConsumableInput"); }));
        return root.toPrettyString();
    }
}
