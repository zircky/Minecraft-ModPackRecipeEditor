package dev.recipeeditor.ui;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeTypeDisplayNameResolverTest {
    @Test void exactSerializerLabelsAreExtensibleWithoutGuessingUnknownMachineNames(){
        final var names=new RecipeTypeDisplayNameResolver();
        assertEquals("Smelting",names.name("minecraft:smelting","en_us"));assertEquals("Furnace",names.machine("minecraft:smelting","en_us"));
        assertEquals("Печь",names.machine("minecraft:smelting","ru_ru"));assertEquals("custom:machine",names.name("custom:machine","en_us"));
        names.register("custom:machine",Map.of("en_us","Verified machine recipe"),Map.of("en_us","Verified machine"));
        assertEquals("Verified machine recipe",names.name("custom:machine","ru_ru"));
        assertThrows(IllegalArgumentException.class,()->names.register("custom:machine",Map.of(),Map.of()));
    }
}
