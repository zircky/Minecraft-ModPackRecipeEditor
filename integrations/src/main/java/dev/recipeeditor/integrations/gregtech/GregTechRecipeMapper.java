package dev.recipeeditor.integrations.gregtech;
import com.fasterxml.jackson.databind.*;
import dev.recipeeditor.registry.ResourceJsonReader;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.integrations.api.*;
import java.util.*;
public final class GregTechRecipeMapper implements RecipeMapper<GregTechRecipeDefinition>, RuntimeRecipeMapper<GregTechRecipeDefinition> {
    @Override public GregTechRecipeDefinition map(final RecipeDefinition recipe) { return map(recipe.id(), recipe.rawJson(), false); }
    @Override public GregTechRecipeDefinition map(final RuntimeData.Recipe recipe) {
        if (!recipe.adapter().equals("gtceu-7.1.4")) throw new IllegalArgumentException("No verified runtime mapping adapter: " + recipe.adapter());
        return map(ResourceLocation.parse(recipe.id()), recipe.properties().toString(), true);
    }
    public GregTechRecipeDefinition map(final ResourceLocation id, final String raw, final boolean runtime) {
        final var root = object(raw);
        final var type = ResourceLocation.parse(root.path("type").asText());
        if (!type.namespace().equals("gtceu")) throw new IllegalArgumentException("Not a GT machine recipe");
        final var contents = new ArrayList<GregTechContent>();
        for (final String role : List.of("inputs", "outputs", "tickInputs", "tickOutputs")) {
            final var caps = root.get(role); if (caps == null) continue;
            if (!caps.isObject()) throw new IllegalArgumentException(role + " must be an object");
            caps.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isArray()) throw new IllegalArgumentException("Content array required: " + role);
                entry.getValue().forEach(value -> contents.add(content(role, entry.getKey(), value)));
            });
        }
        final var conditions = new ArrayList<GregTechCondition>();
        if (root.has("recipeConditions")) {
            if (!root.get("recipeConditions").isArray()) throw new IllegalArgumentException("Conditions must be an array");
            root.get("recipeConditions").forEach(value -> conditions.add(new GregTechCondition(value.path("type").asText(),value.toString())));
        }
        final var extra = new LinkedHashMap<String, String>();
        root.fields().forEachRemaining(entry -> { if (!Set.of("type", "category", "duration", "inputs", "outputs", "tickInputs", "tickOutputs", "recipeConditions").contains(entry.getKey())) extra.put(entry.getKey(), entry.getValue().toString()); });
        return new GregTechRecipeDefinition(id, type, root.has("category") ? Optional.of(ResourceLocation.parse(root.get("category").asText()).toString()) : Optional.empty(),
                integer(root.get("duration")), energy(root, "tickInputs"), energy(root, "tickOutputs"), contents, conditions, extra, raw, runtime);
    }
    public JsonNode object(final String raw) {
        try { final var root = new ResourceJsonReader().read(raw); if (root == null || !root.isObject()) throw new IllegalArgumentException("Recipe object required"); return root; }
        catch (java.io.IOException error) { throw new IllegalArgumentException(error.getMessage(), error); }
    }
    private GregTechContent content(final String role, final String capability, final JsonNode node) {
        if (!node.isObject() || !node.has("content")) throw new IllegalArgumentException("GT content object required");
        final var value = node.get("content");
        final var ingredient = capability.equals("item") ? item(value) : capability.equals("fluid") ? fluid(value) : new IngredientDefinition.Custom(capability, value.toString());
        OptionalLong amount = OptionalLong.empty();
        if (value.isObject()) { final var count = value.has("amount") ? value.get("amount") : value.get("count"); amount = number(count); }
        return new GregTechContent(role, capability, ingredient, amount, integer(node.get("chance")), integer(node.get("maxChance")), integer(node.get("tierChanceBoost")), node.toString());
    }
    private IngredientDefinition item(final JsonNode value) {
        if (value.isArray()) { final var choices = new ArrayList<IngredientDefinition>(); value.forEach(v -> choices.add(item(v))); return new IngredientDefinition.Alternatives(choices); }
        if (value.path("type").asText().equals("gtceu:sized")) {
            final var amount = number(value.get("count"));
            if (amount.isEmpty()) throw new IllegalArgumentException("Sized count must be integral");
            return new IngredientDefinition.Sized(item(value.path("ingredient")), amount.getAsLong(), value.toString());
        }
        if (value.isObject() && value.size() == 1 && value.has("item")) return new IngredientDefinition.Item(ResourceLocation.parse(value.get("item").asText()));
        if (value.isObject() && value.size() == 1 && value.has("tag")) return new IngredientDefinition.Tag(ResourceLocation.parse(value.get("tag").asText()));
        return new IngredientDefinition.Custom(value.path("type").asText("unknown"), value.toString());
    }
    private IngredientDefinition fluid(final JsonNode value) {
        final var amount = number(value.get("amount")); final var references = value.path("value");
        if (amount.isEmpty() || amount.getAsLong() < 1) throw new IllegalArgumentException("Positive fluid amount required");
        final var entries = new ArrayList<IngredientDefinition>();
        if (references.isArray()) references.forEach(v -> entries.add(fluidReference(v, amount.getAsLong())));
        else entries.add(fluidReference(references, amount.getAsLong()));
        return entries.size() == 1 ? entries.getFirst() : new IngredientDefinition.Alternatives(entries);
    }
    private IngredientDefinition fluidReference(final JsonNode node, final long amount) {
        if (node.isObject() && node.size() == 1 && (node.has("fluid") ^ node.has("tag"))) {
            final boolean tag = node.has("tag");
            return new IngredientDefinition.Fluid(ResourceLocation.parse(node.path(tag ? "tag" : "fluid").asText()), tag, amount, node.toString());
        }
        return new IngredientDefinition.Custom("fluid", node.toString());
    }
    private OptionalLong energy(final JsonNode root, final String role) {
        final var values = root.path(role).path("eu"); if (!values.isArray() || values.size() != 1) return OptionalLong.empty();
        final var content = values.get(0).path("content");
        if (content.isIntegralNumber()) return number(content);
        final var voltage = number(content.get("voltage")); final var amperage = number(content.get("amperage"));
        if (voltage.isPresent() && amperage.isPresent()) return OptionalLong.of(Math.multiplyExact(voltage.getAsLong(), amperage.getAsLong()));
        return OptionalLong.empty();
    }
    private OptionalLong number(final JsonNode value) { return value != null && value.isIntegralNumber() && value.canConvertToLong() ? OptionalLong.of(value.longValue()) : OptionalLong.empty(); }
    private OptionalInt integer(final JsonNode value) {
        if (value == null) return OptionalInt.empty();
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new IllegalArgumentException("Expected integer: " + value);
        return OptionalInt.of(value.intValue());
    }
}
