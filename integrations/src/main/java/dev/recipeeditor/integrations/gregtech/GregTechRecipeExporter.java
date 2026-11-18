package dev.recipeeditor.integrations.gregtech;
import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.integrations.api.IntegrationRecipeExporter;
import dev.recipeeditor.recipeengine.editor.ValidationIssue;
import java.util.*;
/** Verified codec subset through the GT KubeJS schema factory; unknown fields fail closed. */
public final class GregTechRecipeExporter implements IntegrationRecipeExporter<GregTechRecipeDefinition> {
    private final Set<String> machines = Set.of("gtceu:assembler", "gtceu:macerator", "gtceu:chemical_reactor");
    public List<String> unsupported(final GregTechRecipeDefinition recipe) {
        final var errors = new ArrayList<String>();
        new GregTechRecipeValidator().validate(recipe).stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR).forEach(i -> errors.add(i.message()));
        if (!machines.contains(recipe.recipeType().toString())) errors.add("Machine type has no verified export profile: " + recipe.recipeType());
        final String prefix=recipe.recipeType().path()+"/";
        if(recipe.id().namespace().equals("minecraft") || !recipe.id().path().startsWith(prefix) || recipe.id().path().length()==prefix.length()) errors.add("GT KubeJS requires exact ID namespace:"+prefix+"name (namespace must not be minecraft); arbitrary ID rewrite is forbidden");
        final var root = new GregTechRecipeMapper().object(recipe.rawJson());
        fields(root, Set.of("type", "category", "duration", "inputs", "outputs", "tickInputs", "tickOutputs", "recipeConditions", "data"), errors);
        for (final String role : List.of("inputs", "outputs", "tickInputs", "tickOutputs")) {
            if (!root.has(role)) continue;
            fields(root.get(role), role.startsWith("tick") ? Set.of("eu") : Set.of("item", "fluid"), errors);
            root.get(role).fields().forEachRemaining(cap -> cap.getValue().forEach(content -> {
                fields(content, Set.of("content", "chance", "maxChance", "tierChanceBoost"), errors);
                final var value = content.path("content");
                if (cap.getKey().equals("item")) item(value, errors);
                else if (cap.getKey().equals("fluid")) {
                    fields(value, Set.of("amount", "value"), errors); positive(value.path("amount"), errors);
                    if (value.path("value").isArray()) value.path("value").forEach(v -> reference(v, "fluid", errors));
                    else reference(value.path("value"), "fluid", errors);
                } else if (cap.getKey().equals("eu")) {
                    if (value.isIntegralNumber()) { if (!value.canConvertToLong() || value.longValue() < 0) errors.add("Invalid EU"); }
                    else { fields(value, Set.of("voltage", "amperage"), errors); positive(value.path("voltage"), errors); if(value.has("amperage")) positive(value.get("amperage"), errors); }
                }
            }));
        }
        if (recipe.contents().stream().noneMatch(c -> c.role().equals("outputs") || c.role().equals("tickOutputs"))) errors.add("No serializable outputs");
        // Arbitrary condition/data codecs can carry script callbacks or unknown semantics.
        // Keep their bytes in the model/editor, and require a verified codec profile for export.
        if (root.has("recipeConditions") && !root.get("recipeConditions").isEmpty()) errors.add("Condition codec is not export-verified");
        if (root.has("data") && !root.get("data").isEmpty()) errors.add("Additional NBT/research data is not export-verified");
        return List.copyOf(errors);
    }
    @Override public String generate(final GregTechRecipeDefinition recipe, final String id, final boolean replace) {
        final var errors = unsupported(recipe); if (!errors.isEmpty()) throw new IllegalArgumentException(errors.toString());
        final var target=dev.recipeeditor.core.ResourceLocation.parse(id);
        if(!target.equals(recipe.id())) throw new IllegalArgumentException("Model/target ID mismatch");
        final String builderId=target.namespace()+":"+target.path().substring(recipe.recipeType().path().length()+1);
        final var encoded = new GregTechRecipeMapper().object(recipe.rawJson()).deepCopy();
        // 7.1.4 EnergyStack(long) means (voltage, amperage=1). Its codec can emit a long,
        // whereas the pinned KubeJS component's legacy reader expects an object.
        for(final String role:List.of("tickInputs","tickOutputs")) encoded.path(role).path("eu").forEach(content->{
            final var value=content.path("content");
            if(value.isIntegralNumber()) {
                final var energy=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                energy.put("voltage",value.longValue());energy.put("amperage",1);
                ((com.fasterxml.jackson.databind.node.ObjectNode)content).set("content",energy);
            }
        });
        final var quote = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            return (replace ? "  event.remove({id: " + quote.writeValueAsString(id) + "});\n" : "")
                    + "  event.recipes.gtceu." + recipe.recipeType().path() + "(" + quote.writeValueAsString(builderId) + ").merge(JSON.parse(" + quote.writeValueAsString(encoded.toString()) + "));\n";
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalArgumentException(error); }
    }
    private void item(final JsonNode value, final List<String> errors) {
        if (value.isArray()) { if(value.isEmpty()) errors.add("Empty alternatives"); value.forEach(v -> item(v, errors)); return; }
        if (value.path("type").asText().equals("gtceu:sized")) {
            fields(value, Set.of("type", "count", "ingredient"), errors); positive(value.path("count"), errors); item(value.path("ingredient"), errors);
        } else reference(value, "item", errors);
    }
    private void reference(final JsonNode value, final String kind, final List<String> errors) {
        fields(value, Set.of(kind, "tag"), errors);
        if (value.has(kind) == value.has("tag")) { errors.add("Exactly one resource ID/tag required"); return; }
        try { dev.recipeeditor.core.ResourceLocation.parse(value.path(value.has(kind) ? kind : "tag").asText()); }
        catch (IllegalArgumentException invalid) { errors.add(invalid.getMessage()); }
    }
    private void positive(final JsonNode node, final List<String> errors) { if(!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 1) errors.add("Positive integral quantity required"); }
    private void fields(final JsonNode node, final Set<String> allowed, final List<String> errors) {
        if (!node.isObject()) { errors.add("Object required"); return; }
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) errors.add("Unverified property: " + key); });
    }
}
