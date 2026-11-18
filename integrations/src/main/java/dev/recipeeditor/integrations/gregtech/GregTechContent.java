package dev.recipeeditor.integrations.gregtech;
import dev.recipeeditor.recipeengine.IngredientDefinition;
import java.util.*;
/** Raw representation retains NBT, circuit selectors and custom ingredient fields. */
public record GregTechContent(String role, String capability, IngredientDefinition ingredient,
                              OptionalLong amount, OptionalInt chance, OptionalInt maxChance,
                              OptionalInt tierChanceBoost, String rawJson) {
    public boolean nonConsumable() { return role.endsWith("Inputs") || role.equals("inputs") ? chance.isPresent() && chance.getAsInt() == 0 : false; }
}
