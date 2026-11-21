package dev.recipeeditor.app;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external") @EnabledIfSystemProperty(named="realModpack",matches=".+")
final class BotaniaRecipeAdapterTest {
    @Test void all202OriginalDefinitionsAreParsedWithoutRawOrSourceLoss()throws Exception {
        try(final var artifact=new BotaniaTestArtifact()) {
            final var recipes=artifact.recipes();assertEquals(202,recipes.size());
            final var counts=recipes.stream().collect(java.util.stream.Collectors.groupingBy(r->r.type().displayId(),java.util.stream.Collectors.counting()));
            assertEquals(Map.of("botania:brew",20L,"botania:petal_apothecary",43L,"botania:mana_infusion",139L),counts);
            final var adapter=new dev.recipeeditor.integrations.botania.BotaniaRecipeAdapter();
            for(final var recipe:recipes){assertEquals(RecipeParseStatus.PARSED,recipe.status(),recipe.id()+" "+recipe.diagnostics());assertEquals(artifact.read(recipe.source().internalPath()),recipe.rawJson());assertEquals(artifact.path,recipe.source().archivePath());assertEquals(List.of("botania"),recipe.source().archiveModIds());assertTrue(adapter.capabilities(recipe).supports(dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_VIEW));assertNotNull(adapter.layout(recipe));}
        }
    }
    @Test void allureIsADeclaredLocalizedBrewNotAnItemAndAnalyzerRemainsConservative()throws Exception {
        try(final var artifact=new BotaniaTestArtifact()) {
            final var recipe=artifact.recipe("data/botania/recipes/brew/allure.json");assertEquals(4,recipe.inputs().size());
            final var brew=assertInstanceOf(RecipeOutput.Brew.class,recipe.outputs().getFirst());assertEquals("botania:allure",brew.definition().id().toString());assertEquals("Marine Allure",brew.definition().displayName("en_us"));
            final var index=new RecipeIndex(List.of(recipe),new TagIndex(List.of()));assertTrue(index.findRecipesProducing(ResourceLocation.parse("botania:allure")).isEmpty());
            assertFalse(new dev.recipeeditor.recipeanalyzer.model.StandardDependencyAdapter().describe(recipe).complete());
            final var display=new dev.recipeeditor.ui.RecipeDisplayNameResolver(new RegistryIndex(List.of()),new dev.recipeeditor.ui.RecipeTypeDisplayNameResolver()).resolve(recipe,"en_us");assertEquals("Brew: Marine Allure",display.name());assertEquals("Botania Brewing",display.type());assertTrue(display.previewOutput().isEmpty());
        }
    }
    @Test void explicitTestMutationsPreserveUnknownFieldsAndRejectMissingRequiredFields()throws Exception {
        try(final var artifact=new BotaniaTestArtifact()) {
            final var original=artifact.recipe("data/botania/recipes/brew/allure.json");
            final var root=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(original.rawJson());root.put("phase16_test_extension",true);
            final String raw=root.toString();final var changed=artifact.validator.validate(original.id(),raw,original.source()).recipe();assertEquals(RecipeParseStatus.PARTIALLY_PARSED,changed.status());assertEquals(raw,changed.rawJson());assertEquals(original.source(),changed.source());
            root.remove("brew");final var invalid=artifact.validator.validate(original.id(),root.toString(),original.source());assertFalse(invalid.valid());assertEquals(RecipeParseStatus.INVALID,invalid.recipe().status());assertTrue(invalid.recipe().outputs().isEmpty());
        }
    }
}
