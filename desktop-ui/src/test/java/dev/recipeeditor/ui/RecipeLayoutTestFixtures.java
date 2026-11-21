package dev.recipeeditor.ui;
import dev.recipeeditor.recipeengine.*;
import java.util.*;
final class RecipeLayoutTestFixtures {
    static RecipeDefinition cooking(){return new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.Vanilla1201Parsers().parsers(),new dev.recipeeditor.registry.RegistryIndex(List.of())).validate("test:unrelated_id","{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:glass\",\"custom\":true}",new RecipeSource(dev.recipeeditor.registry.SourceType.LOCAL_RESOURCE,List.of(),java.nio.file.Path.of("fixture"),"recipe.json","fixture")).recipe();}
}
