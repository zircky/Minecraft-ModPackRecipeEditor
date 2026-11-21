package dev.recipeeditor.ui;
import java.util.*;

/** One cooking template configured by verified Vanilla serializer IDs. */
public final class CookingRecipeLayoutProvider {
    public record Machine(String name,Set<String> properties){public Machine{properties=Set.copyOf(properties);}}
    private final Map<String,Machine> machines=Map.of(
        "minecraft:smelting",machine("Furnace"),"minecraft:blasting",machine("Blast Furnace"),
        "minecraft:smoking",machine("Smoker"),"minecraft:campfire_cooking",machine("Campfire"));
    public Optional<Machine> find(final String type){return Optional.ofNullable(machines.get(type));}
    private Machine machine(final String name){return new Machine(name,Set.of("cookingtime","experience"));}
}
