package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeFixtures {
    private RecipeFixtures() { }
    static RecipeDefinition recipe(final String id,final String file,final List<IngredientDefinition> inputs,final List<RecipeOutput> outputs,final RecipeParseStatus status) {
        return new RecipeDefinition(ResourceLocation.parse(id),new RecipeTypeDefinition("minecraft:crafting_shapeless"),inputs,outputs,new RecipeLayout(),List.of(),new RecipeSource(SourceType.MOD_JAR,List.of("owner"),Path.of(file),"data/test/recipes/a.json","test"),"{raw}",status,List.of());
    }
}
