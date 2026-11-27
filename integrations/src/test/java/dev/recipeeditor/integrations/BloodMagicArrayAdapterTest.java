package dev.recipeeditor.integrations;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.SourceType;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BloodMagicArrayAdapterTest {
    @Test void distinguishesRealCraftedOutputsFromEffectSentinels(){
        final var adapter=new BloodMagicArrayAdapter();
        final var crafted=adapter.parse(context("airsigil","bloodmagic:airsigil")).recipe();
        assertEquals("bloodmagic:airsigil",((RecipeOutput.Fixed)crafted.outputs().getFirst()).id().toString());
        assertInstanceOf(IngredientDefinition.Tag.class,crafted.inputs().getFirst());
        assertEquals(2,adapter.layout(crafted).groups().size());
        final var effect=adapter.parse(context("bounce","minecraft:bedrock")).recipe();
        assertInstanceOf(RecipeOutput.Dynamic.class,effect.outputs().getFirst());
        assertEquals(1,adapter.layout(effect).groups().size());
    }

    private RecipeParseContext context(final String name,final String result){
        return new RecipeParseContext(ResourceLocation.parse("bloodmagic:array/"+name),new RecipeTypeDefinition("bloodmagic:array"),
                new RecipeSource(SourceType.MOD_JAR,List.of("bloodmagic"),Path.of("fixture.jar"),"array/"+name+".json","test"),
                """
                {"type":"bloodmagic:array","baseinput":{"tag":"forge:slimeballs"},
                 "addedinput":{"item":"minecraft:redstone"},"output":{"item":"%s"}}
                """.formatted(result));
    }
}
