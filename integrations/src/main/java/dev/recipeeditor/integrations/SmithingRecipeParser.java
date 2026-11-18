package dev.recipeeditor.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.recipeengine.*;
import java.util.Set;
public final class SmithingRecipeParser extends AbstractVanillaRecipeParser {
    public SmithingRecipeParser() { super(Set.of("smithing_transform","smithing_trim"),Set.of("template","base","addition","result")); }
    @Override protected void parseBody(final JsonNode json,final Draft draft,final RecipeParseContext context) {
        for (final var role:java.util.List.of("template","base","addition")) {
            final var value=ingredient(json.path(role),draft); draft.inputs.add(value); draft.slots.put(role,value);
        }
        if (context.type().location().orElseThrow().path().equals("smithing_trim")) {
            draft.outputs.add(new RecipeOutput.Dynamic("Trim applied to the input base stack; item ID and stack data depend on actual inputs"));
            if (json.has("result")) draft.partial=true;
        } else draft.outputs.add(output(json.path("result"),false,true,draft));
    }
}
