package dev.recipeeditor.ui;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.util.*;
import javafx.scene.Scene;
import javafx.scene.Node;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration")
final class CookingRecipeLayoutTest {
    @ParameterizedTest @ValueSource(strings={"smelting","blasting","smoking","campfire_cooking"})
    void parametersDoNotMoveSlotCentersAndCookingResultStaysAString(final String type)throws Exception {
        final var validator=new RecipeValidator(new Vanilla1201Parsers().parsers(),new RegistryIndex(List.of()));
        final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),java.nio.file.Path.of("fixture"),"recipe.json","fixture");
        final String raw="{\"type\":\"minecraft:"+type+"\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:glass\",\"custom\":true}";
        final var recipe=validator.validate("test:technical",raw,source).recipe();final var model=new RecipeLayoutResolver().resolve(recipe).orElseThrow();
        FxLayoutTestSupport.run(()->{try(final var textures=new TextureResourceService()) {
            for(final int size:List.of(40,64,80))for(final int width:List.of(800,1280)) {
                final var view=new RecipeLayoutRenderer(model,FxLayoutTestSupport.discovery(),textures,size,"en_us",RecipeLayoutRenderer.Mode.VIEWER,null);
                new UiTheme().apply(new Scene(view,width,600));view.applyCss();view.layout();
                final var input=view.lookup(".recipe-input-slot");final var output=view.lookup(".recipe-output-slot");final var arrow=view.lookup(".recipe-arrow");
                assertEquals(center(input),center(output),0.5);assertEquals(center(input),center(arrow),0.5);
                assertEquals(((RecipeSlotView)input).getWidth(),((RecipeSlotView)output).getWidth(),0.1);
                assertEquals(1,view.lookupAll(".recipe-arrow").size());
            }
        }});
        final String changed=new RecipeFormService().result(raw,dev.recipeeditor.core.ResourceLocation.parse("minecraft:diamond"),1);
        assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().readTree(changed).path("result").isTextual());assertTrue(changed.contains("custom"));
        assertThrows(IllegalArgumentException.class,()->new RecipeFormService().result(raw,dev.recipeeditor.core.ResourceLocation.parse("minecraft:diamond"),2));
    }
    private double center(final Node node){final var bounds=node.localToScene(node.getBoundsInLocal());return (bounds.getMinY()+bounds.getMaxY())/2;}
}
