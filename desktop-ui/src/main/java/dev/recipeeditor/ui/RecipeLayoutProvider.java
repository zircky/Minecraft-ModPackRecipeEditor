package dev.recipeeditor.ui;
import java.util.*;

/** Exact Vanilla type mapping. Mod recipes require their own adapter, never a generic crafting grid. */
public final class RecipeLayoutProvider {
    public enum Kind { SHAPED, SHAPELESS, COOKING, STONECUTTING, SMITHING_TRANSFORM, SMITHING_TRIM }
    public record Layout(Kind kind,String workstation,List<String> inputs) { public Layout {inputs=List.copyOf(inputs);} }
    public Optional<Layout> find(final String type) {
        return switch(type){
            case "minecraft:crafting_shaped"->Optional.of(new Layout(Kind.SHAPED,"Crafting table",List.of()));
            case "minecraft:crafting_shapeless"->Optional.of(new Layout(Kind.SHAPELESS,"Shapeless crafting",List.of()));
            case "minecraft:smelting"->cooking("Furnace");case "minecraft:blasting"->cooking("Blast furnace");
            case "minecraft:smoking"->cooking("Smoker");case "minecraft:campfire_cooking"->cooking("Campfire");
            case "minecraft:stonecutting"->Optional.of(new Layout(Kind.STONECUTTING,"Stonecutter",List.of("ingredient")));
            case "minecraft:smithing_transform"->smithing(Kind.SMITHING_TRANSFORM);
            case "minecraft:smithing_trim"->smithing(Kind.SMITHING_TRIM);
            default->Optional.empty();};
    }
    private Optional<Layout> cooking(final String name){return Optional.of(new Layout(Kind.COOKING,name,List.of("ingredient")));}
    private Optional<Layout> smithing(final Kind kind){return Optional.of(new Layout(kind,"Smithing table",List.of("template","base","addition")));}
}
