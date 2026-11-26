package dev.recipeeditor.companionclient;

import com.google.gson.*;
import dev.recipeeditor.companionprotocol.RuntimeData;
import java.util.Set;

/** Projects validated loaded Vanilla state onto a new-ID serializer draft. */
public final class VanillaRuntimeDraft {
    private static final Set<String> COOKING = Set.of("smelting", "blasting", "smoking", "campfire_cooking");
    private static final Set<String> COMMON = Set.of("group", "sourceJsonAvailable", "ingredients", "result");

    public boolean supports(final RuntimeData.Recipe recipe) {
        if (!recipe.adapter().equals("vanilla-1.20.1") || recipe.status() != RuntimeData.Support.SUPPORTED) return false;
        try { copy(recipe); return true; }
        catch (RuntimeException bad) { return false; }
    }

    public String copy(final RuntimeData.Recipe recipe) {
        require(recipe.adapter().equals("vanilla-1.20.1") && recipe.status() == RuntimeData.Support.SUPPORTED
                && recipe.serializer().startsWith("minecraft:"));
        final String kind = recipe.serializer().substring("minecraft:".length());
        final boolean shaped = kind.equals("crafting_shaped");
        final boolean shapeless = kind.equals("crafting_shapeless");
        final boolean cooking = COOKING.contains(kind);
        final boolean stone = kind.equals("stonecutting");
        require(shaped || shapeless || cooking || stone);
        require(recipe.type().equals("minecraft:" + (shaped || shapeless ? "crafting" : kind)));
        final var state = recipe.properties();
        final Set<String> extra = shaped ? Set.of("width","height","category","showNotification")
                : shapeless ? Set.of("category") : cooking ? Set.of("category","experience","cookingTime") : Set.of();
        require(state.keySet().stream().allMatch(key -> COMMON.contains(key) || extra.contains(key)));
        require(state.has("ingredients") && state.has("result"));
        final var ingredients = state.getAsJsonArray("ingredients");
        final var result = state.getAsJsonObject("result");
        require(Set.of("item","count","empty").equals(result.keySet()) && result.get("empty").isJsonPrimitive()
                && result.getAsJsonPrimitive("empty").isBoolean() && !result.get("empty").getAsBoolean());
        final String output = location(result.get("item"));
        final int count = integer(result.get("count"), 1, 64);
        require(!output.equals("minecraft:air") && (!cooking || count == 1));
        final var draft = new JsonObject();
        draft.addProperty("type", recipe.serializer());
        text(state, draft, "group");
        if (shaped || shapeless || cooking) text(state, draft, "category");
        if (shaped) {
            final int width = integer(state.get("width"), 1, 3);
            final int height = integer(state.get("height"), 1, 3);
            require(ingredients.size() == width * height);
            final var pattern = new JsonArray();
            final var keys = new JsonObject();
            for (int y = 0; y < height; y++) {
                final var row = new StringBuilder();
                for (int x = 0; x < width; x++) {
                    final var ingredient = ingredients.get(y * width + x);
                    if (ingredient.isJsonArray() && ingredient.getAsJsonArray().isEmpty()) row.append(' ');
                    else {
                        require(ingredient(ingredient));
                        final char symbol = (char)('A' + y * width + x);
                        keys.add(String.valueOf(symbol), ingredient);
                        row.append(symbol);
                    }
                }
                pattern.add(row.toString());
            }
            require(keys.size() > 0);
            draft.add("pattern", pattern);
            draft.add("key", keys);
            if (state.has("showNotification")) {
                require(state.get("showNotification").isJsonPrimitive() && state.getAsJsonPrimitive("showNotification").isBoolean());
                draft.add("show_notification", state.get("showNotification"));
            }
        } else {
            require(!ingredients.isEmpty() && ingredients.size() <= (shapeless ? 9 : 1));
            require(ingredients.asList().stream().allMatch(this::ingredient));
            draft.add(shapeless ? "ingredients" : "ingredient", shapeless ? ingredients : ingredients.get(0));
        }
        if (cooking || stone) {
            draft.addProperty("result", output);
            if (stone) draft.addProperty("count", count);
            else {
                final var experience = state.get("experience");
                require(experience != null && experience.isJsonPrimitive() && experience.getAsJsonPrimitive().isNumber()
                        && Float.isFinite(experience.getAsFloat()) && experience.getAsFloat() >= 0);
                draft.add("experience", experience);
                draft.addProperty("cookingtime", integer(state.get("cookingTime"), 1, Integer.MAX_VALUE));
            }
        } else {
            final var stack = new JsonObject();
            stack.addProperty("item", output);
            stack.addProperty("count", count);
            draft.add("result", stack);
        }
        return draft.toString();
    }

    private void text(final JsonObject state, final JsonObject draft, final String field) {
        if (state.has(field)) {
            require(state.get(field).isJsonPrimitive() && state.getAsJsonPrimitive(field).isString());
            draft.add(field, state.get(field));
        }
    }

    private boolean ingredient(final JsonElement value) {
        if (value.isJsonArray()) return !value.getAsJsonArray().isEmpty() && value.getAsJsonArray().asList().stream()
                .allMatch(item -> item.isJsonObject() && ingredient(item));
        if (!value.isJsonObject() || value.getAsJsonObject().size() != 1) return false;
        final var object = value.getAsJsonObject();
        final var id = object.has("item") ? object.get("item") : object.get("tag");
        return id != null && !location(id).equals("minecraft:air");
    }

    private String location(final JsonElement value) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString());
        RuntimeData.location(value.getAsString());
        return value.getAsString();
    }

    private int integer(final JsonElement value, final int min, final int max) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber());
        final int result;
        try { result = value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("Non-integral Vanilla property", invalid); }
        require(result >= min && result <= max);
        return result;
    }

    private void require(final boolean supported) {
        if (!supported) throw new IllegalArgumentException("Runtime properties cannot be represented by the verified Vanilla serializer");
    }
}
