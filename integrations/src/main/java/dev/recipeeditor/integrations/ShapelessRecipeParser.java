package dev.recipeeditor.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.recipeengine.*;
import java.util.Set;
public final class ShapelessRecipeParser extends AbstractVanillaRecipeParser {
    public ShapelessRecipeParser() { super(Set.of("crafting_shapeless"),Set.of("ingredients","result")); }
    @Override protected void parseBody(final JsonNode json,final Draft draft,final RecipeParseContext context) {
        final var ingredients=json.path("ingredients");
        if (!ingredients.isArray() || ingredients.isEmpty() || ingredients.size()>9) throw new IllegalArgumentException("Shapeless requires 1..9 ingredients");
        ingredients.forEach(value->draft.inputs.add(ingredient(value,draft)));
        draft.outputs.add(output(json.path("result"),false,false,draft));
    }
}
