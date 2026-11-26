package dev.recipeeditor.recipeengine.editor;

import java.util.*;

/** Explicit controls for the verified 1.20.1 Vanilla forms. */
public final class VanillaRecipeSchema {
    public enum Kind { STRING, ENUM, BOOLEAN, INTEGER, DECIMAL }
    public record Field(String key, Kind kind, String defaultJson, List<String> choices) {
        public Field { choices = List.copyOf(choices); }
    }

    private static final Set<String> COOKING = Set.of("minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking");
    private static final List<String> CRAFTING_CATEGORIES = List.of("building", "redstone", "equipment", "misc");
    private static final List<String> COOKING_CATEGORIES = List.of("food", "blocks", "misc");

    public List<Field> fields(final String type) {
        final var fields = new ArrayList<Field>();
        if (COOKING.contains(type)) {
            fields.add(new Field("category", Kind.ENUM, "\"misc\"", COOKING_CATEGORIES));
            fields.add(new Field("experience", Kind.DECIMAL, "0", List.of()));
            final String duration = type.equals("minecraft:smelting") ? "200"
                    : type.equals("minecraft:campfire_cooking") ? "600" : "100";
            fields.add(new Field("cookingtime", Kind.INTEGER, duration, List.of()));
        } else if (type.equals("minecraft:crafting_shaped") || type.equals("minecraft:crafting_shapeless")) {
            fields.add(new Field("category", Kind.ENUM, "\"misc\"", CRAFTING_CATEGORIES));
            if (type.equals("minecraft:crafting_shaped"))
                fields.add(new Field("show_notification", Kind.BOOLEAN, "true", List.of()));
        }
        if (type.equals("minecraft:stonecutting") || !fields.isEmpty())
            fields.addFirst(new Field("group", Kind.STRING, "\"\"", List.of()));
        return List.copyOf(fields);
    }
}
