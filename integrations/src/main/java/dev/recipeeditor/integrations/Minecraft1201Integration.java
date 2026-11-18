package dev.recipeeditor.integrations;

import dev.recipeeditor.registry.ResourceLayout;
import dev.recipeeditor.recipeengine.RecipeParser;
import java.util.List;
/** Version-scoped capability; future releases get their own implementation and parser set. */
public final class Minecraft1201Integration {
    public boolean supports(final String version) { return version.equals("1.20.1"); }
    public ResourceLayout layout() { return ResourceLayout.minecraft1201(); }
    public List<RecipeParser> parsers() { return new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(); }
}
