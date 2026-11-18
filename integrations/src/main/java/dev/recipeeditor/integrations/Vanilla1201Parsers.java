package dev.recipeeditor.integrations;

import dev.recipeeditor.recipeengine.RecipeParser;
import java.util.List;
public final class Vanilla1201Parsers {
    public List<RecipeParser> parsers() { return List.of(new ShapedRecipeParser(),new ShapelessRecipeParser(),new CookingRecipeParser(),new StonecuttingRecipeParser(),new SmithingRecipeParser()); }
}
