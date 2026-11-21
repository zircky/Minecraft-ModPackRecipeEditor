package dev.recipeeditor.integrations.api;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.RecipeValidator;
import dev.recipeeditor.registry.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ModdedRecipeParserTest {
    @Test void missingRequiredBrewFieldsAreInvalidRatherThanUnsupportedAndRawSourceIsRetained(){
        // Deliberately incomplete version of the verified serializer, not a sample recipe.
        final String invalid="{\"type\":\"botania:brew\"}";
        final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),java.nio.file.Path.of("invalid-input.json"),"recipe.json","negative test");
        final var value=new RecipeValidator(new ProjectRecipeParsers().parsers(),new RegistryIndex(List.of())).validate("botania:brew/allure",invalid,source);
        assertFalse(value.valid());assertEquals(RecipeParseStatus.INVALID,value.recipe().status());assertEquals(invalid,value.recipe().rawJson());assertEquals(source,value.recipe().source());assertTrue(value.recipe().outputs().isEmpty());
        final var caps=new dev.recipeeditor.integrations.botania.BotaniaRecipeAdapter().capabilities(value.recipe());
        assertFalse(caps.supports(RecipeTypeCapabilities.Feature.CAN_VIEW));assertFalse(caps.supports(RecipeTypeCapabilities.Feature.CAN_EDIT));assertFalse(caps.supports(RecipeTypeCapabilities.Feature.CAN_CREATE));assertFalse(caps.supports(RecipeTypeCapabilities.Feature.CAN_EXPORT));
    }
}
