package dev.recipeeditor.integrations.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class EditorFixtures {
    private EditorFixtures() { }
    static final String RAW="{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"test:ore\"},\"result\":\"test:ingot\",\"experience\":0.5}";
    static RecipeValidator validator() { return new RecipeValidator(new Vanilla1201Parsers().parsers(),new RegistryIndex(List.of())); }
    static RecipeDefinition recipe(final String id,final String raw) { return validator().validate(id,raw,new RecipeSource(SourceType.MOD_JAR,List.of("test"),Path.of("base.jar"),"data/test/recipes/a.json","Static resource scan")).recipe(); }
    static RecipeEditorService service() { return new RecipeEditorService(new RecipeIndex(List.of(recipe("test:base",RAW)),new TagIndex(List.of())),validator()); }
}
