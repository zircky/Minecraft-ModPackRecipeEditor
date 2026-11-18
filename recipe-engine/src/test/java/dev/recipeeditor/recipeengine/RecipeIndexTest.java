package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeIndexTest {
    private final ResourceLocation item=ResourceLocation.parse("test:a"),output=ResourceLocation.parse("test:b");
    private RecipeDefinition recipe() { return RecipeFixtures.recipe("test:recipe","a.jar",List.of(new IngredientDefinition.Alternatives(List.of(new IngredientDefinition.Item(item),new IngredientDefinition.Tag(ResourceLocation.parse("forge:ingots"))))),List.of(new RecipeOutput.Fixed(output,2,false,"")),RecipeParseStatus.PARSED); }
    @Test void indexedQueries() {
        final var index=new RecipeIndex(List.of(recipe()),new TagIndex(List.of()));
        assertEquals(1,index.findById(ResourceLocation.parse("test:recipe")).size());
        assertEquals(1,index.findRecipesUsing(item).size()); assertEquals(1,index.findRecipesProducing(output).size());
        assertEquals(1,index.findRecipesByType(ResourceLocation.parse("minecraft:crafting_shapeless")).size());
        assertEquals(1,index.findRecipesByTag(ResourceLocation.parse("forge:ingots")).size());
        assertTrue(index.findRecipesUsing(ResourceLocation.parse("test:absent")).isEmpty());
        assertEquals(1,index.byMod("owner").size()); assertEquals(1,index.byNamespace("test").size());
    }
    @Test void unsupportedDoesNotLeakFalseIngredientMatches() {
        final var unknown=RecipeFixtures.recipe("test:unknown","a.jar",List.of(new IngredientDefinition.Item(item)),List.of(new RecipeOutput.Fixed(output,1,false,"")),RecipeParseStatus.UNSUPPORTED);
        final var index=new RecipeIndex(List.of(unknown),new TagIndex(List.of()));
        assertTrue(index.findRecipesUsing(item).isEmpty()); assertTrue(index.findRecipesProducing(output).isEmpty()); assertEquals(1,index.recipes().size());
    }
    @Test void dynamicTrimOutputHasNoFixedSearchMatch() {
        final var recipe=RecipeFixtures.recipe("test:trim","a.jar",List.of(new IngredientDefinition.Item(item)),List.of(new RecipeOutput.Dynamic("base stack")),RecipeParseStatus.PARSED);
        final var index=new RecipeIndex(List.of(recipe),new TagIndex(List.of()));
        assertEquals(1,index.findRecipesUsing(item).size()); assertTrue(index.findRecipesProducing(item).isEmpty());
    }
    @Test void knownTagMembersAreIndexedAsCandidates() {
        final var source=new ResourceSource(SourceType.MOD_JAR,List.of(),Path.of("tags.jar"),"data/forge/tags/items/ingots.json","test");
        final var tags=new TagIndex(List.of(new ResourceDocument(source,"{\"values\":[\"test:metal\"]}")));
        final var index=new RecipeIndex(List.of(recipe()),tags);
        assertEquals(1,index.findRecipesUsing(ResourceLocation.parse("test:metal")).size());
    }
    @Test void typeFiltersNormalizeDefaultMinecraftNamespace() {
        final var original=recipe();
        final var bare=new RecipeDefinition(original.id(),new RecipeTypeDefinition("crafting_shapeless"),original.inputs(),original.outputs(),original.layout(),original.properties(),original.source(),original.rawJson(),original.status(),original.diagnostics());
        final var service=new RecipeSearchService(new RecipeIndex(List.of(bare),new TagIndex(List.of())));
        assertEquals(1,service.search("","","minecraft:crafting_shapeless").size());
    }
}
