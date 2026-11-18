package dev.recipeeditor.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.recipeengine.*;
import java.util.Set;
public final class CookingRecipeParser extends AbstractVanillaRecipeParser {
    public CookingRecipeParser() { super(Set.of("smelting","blasting","smoking","campfire_cooking"),Set.of("ingredient","result","experience","cookingtime")); }
    @Override protected void parseBody(final JsonNode json,final Draft draft,final RecipeParseContext context) {
        draft.inputs.add(ingredient(json.path("ingredient"),draft));
        draft.outputs.add(output(json.path("result"),true,false,draft));
        if (json.has("experience") && (!json.path("experience").isNumber() || json.path("experience").doubleValue()<0 || !Double.isFinite(json.path("experience").doubleValue())))
            throw new IllegalArgumentException("Invalid experience");
        if (json.has("cookingtime")) positiveInteger(json.path("cookingtime"),"cooking time");
        else {
            final String type=context.type().location().orElseThrow().path();
            final int ticks=type.equals("smelting")?200:type.equals("campfire_cooking")?600:100;
            draft.properties.add(new RecipeProperty("cookingtime",Integer.toString(ticks)));
        }
        if (!json.has("experience")) draft.properties.add(new RecipeProperty("experience","0.0"));
    }
}
