package dev.recipeeditor.integrations;
import dev.recipeeditor.recipeengine.RecipeParser;
import dev.recipeeditor.integrations.gregtech.GregTechRecipeParser;
import java.util.*;
public final class ProjectRecipeParsers {
    public List<RecipeParser> parsers() {
        final var parsers = new ArrayList<>(new Vanilla1201Parsers().parsers());
        parsers.add(new GregTechRecipeParser()); return List.copyOf(parsers);
    }
}
