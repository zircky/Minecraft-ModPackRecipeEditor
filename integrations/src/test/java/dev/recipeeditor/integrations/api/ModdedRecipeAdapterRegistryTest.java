package dev.recipeeditor.integrations.api;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.botania.BotaniaRecipeAdapter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class ModdedRecipeAdapterRegistryTest {
    @Test void exactSerializerKeysDoNotGrantSupportToAnEntireNamespace(){final var registry=new ModdedRecipeAdapterRegistry(List.of(new BotaniaRecipeAdapter()));assertEquals(3,registry.types().size());assertTrue(registry.supports(ResourceLocation.parse("botania:brew")));assertFalse(registry.supports(ResourceLocation.parse("botania:pure_daisy")));assertFalse(registry.supports(ResourceLocation.parse("minecraft:brew")));}
    @Test void duplicateSerializersFailFast(){assertThrows(IllegalArgumentException.class,()->new ModdedRecipeAdapterRegistry(List.of(new BotaniaRecipeAdapter(),new BotaniaRecipeAdapter())));}
}
