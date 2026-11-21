package dev.recipeeditor.exporters;

import dev.recipeeditor.core.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeDataPathResolverTest {
    @Test void preservesNamespacesNestedPathsAndFileNames(){final var resolver=new RecipeDataPathResolver();assertEquals("data/minecraft/recipes/tools/iron_hammer.json",resolver.resolve("1.20.1",ResourceLocation.parse("minecraft:tools/iron_hammer")));assertEquals("data/gtceu/recipes/example/custom_recipe.json",resolver.resolve("1.20.1",ResourceLocation.parse("gtceu:example/custom_recipe")));}
    @Test void unknownVersionsAndUnsafeWindowsPathsFailClosed(){final var resolver=new RecipeDataPathResolver();assertThrows(IllegalArgumentException.class,()->resolver.resolve("1.21.1",ResourceLocation.parse("test:recipe")));assertThrows(IllegalArgumentException.class,()->resolver.resolve("1.20.1",ResourceLocation.parse("test:../escape")));assertThrows(IllegalArgumentException.class,()->resolver.resolve("1.20.1",ResourceLocation.parse("test:con")));}
}
