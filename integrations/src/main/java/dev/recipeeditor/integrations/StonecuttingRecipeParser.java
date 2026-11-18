package dev.recipeeditor.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.util.Set;
public final class StonecuttingRecipeParser extends AbstractVanillaRecipeParser {
    public StonecuttingRecipeParser() { super(Set.of("stonecutting"),Set.of("ingredient","result","count")); }
    @Override protected void parseBody(final JsonNode json,final Draft draft,final RecipeParseContext context) {
        draft.inputs.add(ingredient(json.path("ingredient"),draft));
        if (!json.path("result").isTextual()) throw new IllegalArgumentException("1.20.1 stonecutting result must be a string");
        draft.outputs.add(new RecipeOutput.Fixed(ResourceLocation.parse(json.path("result").asText()),positiveInteger(json.path("count"),"count"),false,json.path("result").toString()));
    }
}
