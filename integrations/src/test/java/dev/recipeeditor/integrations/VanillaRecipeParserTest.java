package dev.recipeeditor.integrations;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
final class VanillaRecipeParserTest {
    private RecipeDefinition parse(final String raw) {
        final var source=new ResourceSource(SourceType.MOD_JAR,List.of("test"),Path.of("test.jar"),"data/test/recipes/test.json","test");
        return new StaticRecipeScanner(new Vanilla1201Parsers().parsers(),ResourceLayout.minecraft1201()).scan(List.of(new ResourceDocument(source,raw)),new ArrayList<>()).getFirst();
    }
    @Test void shapedPatternKeyAlternativesAndCount() {
        final var recipe=parse("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [\"AA\", \" B\"], \"key\": {\"A\": {\"item\": \"test:a\"}, \"B\": [{\"tag\": \"forge:b\"}, {\"item\": \"test:c\"}]}, \"result\": {\"item\": \"test:result\", \"count\": 2}}");
        assertEquals(RecipeParseStatus.PARSED,recipe.status()); assertEquals(3,recipe.inputs().size());
        assertEquals(2,recipe.layout().pattern().size()); assertInstanceOf(IngredientDefinition.Alternatives.class,recipe.layout().slots().get("B"));
        assertEquals(2,((RecipeOutput.Fixed)recipe.outputs().getFirst()).count());
    }
    @Test void shapeless() { final var recipe=parse("{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [{\"item\": \"test:a\"}, {\"tag\": \"forge:b\"}], \"result\": {\"item\": \"test:result\"}}"); assertEquals(RecipeParseStatus.PARSED,recipe.status()); assertEquals(2,recipe.inputs().size()); }
    @Test void stonecuttingUsesStringResultAndTopLevelCount() {
        final var recipe=parse("{\"type\": \"minecraft:stonecutting\", \"ingredient\": {\"item\": \"test:a\"}, \"result\": \"test:b\", \"count\": 3}"); assertEquals(RecipeParseStatus.PARSED,recipe.status()); assertEquals(3,((RecipeOutput.Fixed)recipe.outputs().getFirst()).count());
    }
    @Test void transformCopiesBaseData() {
        final var recipe=parse("{\"type\": \"minecraft:smithing_transform\", \"template\": {\"item\": \"test:template\"}, \"base\": {\"tag\": \"minecraft:base\"}, \"addition\": {\"item\": \"test:add\"}, \"result\": {\"item\": \"test:upgraded\"}}"); assertEquals(RecipeParseStatus.PARSED,recipe.status()); assertTrue(((RecipeOutput.Fixed)recipe.outputs().getFirst()).copiesBaseData()); assertEquals(3,recipe.layout().slots().size());
    }
    @Test void trimDoesNotInventFixedOutput() {
        final var recipe=parse("{\"type\": \"minecraft:smithing_trim\", \"template\": {\"item\": \"test:template\"}, \"base\": {\"tag\": \"minecraft:base\"}, \"addition\": {\"item\": \"test:add\"}}"); assertEquals(RecipeParseStatus.PARSED,recipe.status()); assertInstanceOf(RecipeOutput.Dynamic.class,recipe.outputs().getFirst());
    }
    @ParameterizedTest @ValueSource(strings={"smelting","blasting","smoking","campfire_cooking"})
    void cookingRealStructureAndDefaultTimes(final String type) {
        final var recipe=parse("{\"type\":\"minecraft:"+type+"\",\"ingredient\":{\"item\":\"test:a\"},\"result\":\"test:b\",\"experience\":0.7}");
        assertEquals(RecipeParseStatus.PARSED,recipe.status());
        assertTrue(recipe.properties().stream().anyMatch(p->p.name().equals("cookingtime")));
        assertTrue(recipe.properties().stream().anyMatch(p->p.name().equals("experience") && p.rawValue().equals("0.7")));
    }
    @Test void unknownTypeRetainsRawWithoutInventedInputs() {
        final String raw="{\"type\":\"create:mechanical_crafting\",\"ingredient\":{\"item\":\"test:a\"},\"custom\":42}";
        final var recipe=parse(raw); assertEquals(RecipeParseStatus.UNSUPPORTED,recipe.status()); assertEquals(raw,recipe.rawJson()); assertTrue(recipe.inputs().isEmpty()); assertTrue(recipe.outputs().isEmpty());
    }
    @Test void unknownFieldsAndConditionsAreRetained() {
        final String raw="{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [{\"item\": \"test:a\"}, {\"tag\": \"forge:b\"}], \"result\": {\"item\": \"test:result\"}, \"conditions\": [{\"type\": \"forge:mod_loaded\", \"modid\": \"other\"}], \"future\": {\"arbitrary\": 42}}";
        final var recipe=parse(raw); assertEquals(RecipeParseStatus.PARTIALLY_PARSED,recipe.status()); assertEquals(raw,recipe.rawJson()); assertTrue(recipe.properties().stream().anyMatch(p->p.name().equals("future")));
    }
    @Test void customIngredientIsNotGuessed() {
        final var recipe=parse("{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [{\"type\": \"forge:custom\", \"item\": \"test:misleading\"}], \"result\": {\"item\": \"test:result\"}}");
        assertEquals(RecipeParseStatus.PARTIALLY_PARSED,recipe.status()); assertInstanceOf(IngredientDefinition.Unknown.class,recipe.inputs().getFirst());
    }
    @ParameterizedTest @ValueSource(strings={"{}","not json","{\"type\":\"minecraft:crafting_shaped\"}","{\"type\":\"minecraft:smithing_trim\"}"})
    void invalidIsDistinctFromUnsupported(final String raw) { assertEquals(RecipeParseStatus.INVALID,parse(raw).status()); }
    @Test void ambiguousParserSelectionIsDeterministic() {
        final var diagnostics=new ArrayList<dev.recipeeditor.core.importing.ImportDiagnostic>();
        final var scanner=new StaticRecipeScanner(List.of(new ShapelessRecipeParser(),new ShapelessRecipeParser()),ResourceLayout.minecraft1201());
        final var source=new ResourceSource(SourceType.MOD_JAR,List.of(),Path.of("test.jar"),"data/test/recipes/a.json","test");
        final var result=scanner.scan(List.of(new ResourceDocument(source,"{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [{\"item\": \"test:a\"}, {\"tag\": \"forge:b\"}], \"result\": {\"item\": \"test:result\"}}")),diagnostics);
        assertEquals(RecipeParseStatus.PARSED,result.getFirst().status()); assertTrue(diagnostics.stream().anyMatch(d->d.message().contains("Ambiguous")));
    }
    @Test void utf8BomIsAcceptedWithoutLosingRawText() {
        final String raw="\uFEFF{\"type\":\"test:custom\"}";
        final var recipe=parse(raw); assertEquals(RecipeParseStatus.UNSUPPORTED,recipe.status()); assertEquals(raw,recipe.rawJson());
    }
}
