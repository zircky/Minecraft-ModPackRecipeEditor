package dev.recipeeditor.ui;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeDisplayNameResolverTest {
    @Test void namesComeFromOutputsWhileIdAndMultipleDefinitionsRemainDistinct(){
        final var id=ResourceLocation.parse("minecraft:stone");
        final var entry=new RegistryEntry(id,ResourceKind.ITEM,"Stone","block.minecraft.stone",Map.of("en_us","Stone","ru_ru","Камень"),List.of(),List.of(),DiscoveryConfidence.INFERRED_FROM_RESOURCES,"fixture");
        final var resolver=new RecipeDisplayNameResolver(new RegistryIndex(List.of(entry)),new RecipeTypeDisplayNameResolver());
        final var recipe=recipe("test:technical_name",List.of(new RecipeOutput.Fixed(id,2,false,"{}")));
        assertEquals("Камень",resolver.resolve(recipe,"ru_ru").name());assertEquals("test:technical_name",recipe.id().toString());
        assertEquals("Stone",resolver.resolve(recipe,"fr_fr").name());
        final var multiple=recipe("test:another_id",List.of(new RecipeOutput.Fixed(id,1,false,"{}"),new RecipeOutput.Dynamic("context dependent")));
        assertEquals(1,resolver.resolve(multiple,"en_us").additionalOutputs());assertNotEquals(recipe.id(),multiple.id());
        assertEquals("Dynamic result",resolver.resolve(recipe("test:dynamic",List.of(new RecipeOutput.Dynamic("Trim"))),"en_us").name());
        assertEquals("Unknown result",resolver.resolve(recipe("test:no_result",List.of()),"en_us").name());
    }
    private RecipeDefinition recipe(final String id,final List<RecipeOutput> outputs){return new RecipeDefinition(ResourceLocation.parse(id),new RecipeTypeDefinition("minecraft:smelting"),List.of(),outputs,new RecipeLayout(),List.of(),new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),java.nio.file.Path.of("test"),"recipe.json","fixture"),"{}",RecipeParseStatus.PARSED,List.of());}
}
