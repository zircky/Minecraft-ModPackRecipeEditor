package dev.recipeeditor.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeLayoutProviderTest {
    @Test void mapsVanillaWorkstationsAndDynamicSmithingWithoutClaimingModSupport() {
        final var provider=new RecipeLayoutProvider();
        for(final String type:java.util.List.of("smelting","blasting","smoking","campfire_cooking"))assertEquals(RecipeLayoutProvider.Kind.COOKING,provider.find("minecraft:"+type).orElseThrow().kind());
        assertEquals(RecipeLayoutProvider.Kind.SHAPED,provider.find("minecraft:crafting_shaped").orElseThrow().kind());
        assertEquals(RecipeLayoutProvider.Kind.SHAPELESS,provider.find("minecraft:crafting_shapeless").orElseThrow().kind());
        assertEquals(RecipeLayoutProvider.Kind.SMITHING_TRIM,provider.find("minecraft:smithing_trim").orElseThrow().kind());
        assertEquals(java.util.List.of("template","base","addition"),provider.find("minecraft:smithing_transform").orElseThrow().inputs());
        assertEquals(RecipeLayoutProvider.Kind.STONECUTTING,provider.find("minecraft:stonecutting").orElseThrow().kind());
        assertTrue(provider.find("create:sequenced_assembly").isEmpty());assertTrue(provider.find("gtceu:assembler").isEmpty());
    }
}
