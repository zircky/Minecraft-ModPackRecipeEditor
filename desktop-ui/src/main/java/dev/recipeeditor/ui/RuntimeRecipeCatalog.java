package dev.recipeeditor.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.recipeeditor.companionclient.RecipeMergeService;
import dev.recipeeditor.companionclient.VanillaRuntimeDraft;
import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.api.IntegrationContext;
import dev.recipeeditor.integrations.gregtech.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.TagIndex;
import java.nio.file.Path;
import java.util.*;

/** Builds immutable display and authoring projections on the editor worker. */
final class RuntimeRecipeCatalog {
    record Catalog(RecipeIndex visible, RecipeIndex authorable, Map<ResourceLocation, RecipeMergeService.Entry> evidence,
                   Set<ResourceLocation> incompatible, String minecraftVersion) {}

    Catalog prepare(final RecipeMergeService.Result merge, final RuntimeData.Snapshot snapshot, final TagIndex tags,
                    final RecipeValidator validator, final IntegrationContext integration) {
        final var evidence = new LinkedHashMap<ResourceLocation, RecipeMergeService.Entry>();
        final var visible = new ArrayList<RecipeDefinition>();
        final var bases = new ArrayList<RecipeDefinition>();
        final var incompatible = new HashSet<>(merge.changedOrMissing());
        final var instance = Path.of(snapshot.environment().sourceInstance());
        for (final var entry : merge.entries()) {
            if (entry.runtime() == null) continue;
            evidence.put(entry.id(), entry);
            final var observed = entry.runtime();
            final var projected = verified(entry.id(), observed, instance, validator, integration);
            final boolean singleStatic = entry.staticDefinitions().size() == 1 && !incompatible.contains(entry.id())
                    && !observed.properties().has("runtimeVariants");
            if (projected.isPresent()) {
                visible.add(projected.get());
                if (entry.staticDefinitions().isEmpty()) bases.add(projected.get());
                else if (singleStatic) bases.add(entry.staticDefinitions().getFirst());
                else incompatible.add(entry.id());
            } else if (singleStatic && entry.staticDefinitions().getFirst().type().displayId().equals(observed.serializer())) {
                visible.add(entry.staticDefinitions().getFirst());
            } else {
                visible.add(inspect(entry));
            }
        }
        return new Catalog(new RecipeIndex(visible, tags), new RecipeIndex(bases, tags), Collections.unmodifiableMap(evidence),
                Set.copyOf(incompatible), snapshot.environment().minecraftVersion());
    }

    private Optional<RecipeDefinition> verified(final ResourceLocation id, final RuntimeData.Recipe observed,
            final Path instance, final RecipeValidator validator, final IntegrationContext integration) {
        try {
            final String raw;
            if (observed.adapter().equals("vanilla-1.20.1") && observed.status() == RuntimeData.Support.SUPPORTED) {
                raw = new VanillaRuntimeDraft().copy(observed);
            } else if (observed.adapter().equals("gtceu-7.1.4")
                    && observed.serializer().equals("gtceu:machine")
                    && new GregTechIntegration().capabilities(integration).enabled("Visual Editing")) {
                raw = new GregTechRuntimeDraft().copy(observed);
                final var model = new GregTechRecipeMapper().map(id,raw,false);
                if (!new GregTechRecipeExporter().unsupported(model).isEmpty()) return Optional.empty();
            } else return Optional.empty();
            final var source = new RuntimeRecipeBase().source(instance, observed.adapter(), observed.type(), observed.serializer());
            final var valid = validator.validate(id, raw, source);
            return valid.valid() && valid.recipe().status() != RecipeParseStatus.UNSUPPORTED ? Optional.of(valid.recipe()) : Optional.empty();
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    private RecipeDefinition inspect(final RecipeMergeService.Entry entry) {
        final var observed = entry.runtime();
        final var state = observed.properties();
        final var source = entry.provenance().activeDefinition().orElseThrow();
        final var inputs = new ArrayList<IngredientDefinition>();
        final var outputs = new ArrayList<RecipeOutput>();
        final var properties = new ArrayList<RecipeProperty>();
        final String type = observed.adapter().equals("vanilla-1.20.1") ? observed.serializer() : observed.type();
        try {
            if (state.has("ingredients") && state.get("ingredients").isJsonArray())
                state.getAsJsonArray("ingredients").forEach(value->{ if (!value.isJsonArray() || !value.getAsJsonArray().isEmpty()) inputs.add(ingredient(value)); });
            if (state.has("dynamicResult") && state.get("dynamicResult").getAsBoolean())
                outputs.add(new RecipeOutput.Dynamic("Output depends on input stacks"));
            if (state.has("result") && state.get("result").isJsonObject()) {
                final var item = state.getAsJsonObject("result");
                if (item.has("item") && item.has("count") && item.get("count").getAsInt() > 0
                        && !item.get("item").getAsString().equals("minecraft:air"))
                    outputs.add(new RecipeOutput.Fixed(ResourceLocation.parse(item.get("item").getAsString()),
                            item.get("count").getAsInt(), false, item.toString()));
            }
            final var machine = state.has("codecPayload") && state.get("codecPayload").isJsonObject()
                    ? state.getAsJsonObject("codecPayload") : state;
            if (observed.adapter().startsWith("gtceu-")) {
                slots(machine, "inputs", inputs, outputs);
                slots(machine, "outputs", inputs, outputs);
                for (final String key : List.of("duration","inputEUt","outputEUt","category","recipeConditions","conditions","data","tickInputs","tickOutputs"))
                    if (machine.has(key)) properties.add(new RecipeProperty(key,machine.get(key).toString()));
            } else {
                for (final String key : List.of("cookingTime","experience","group"))
                    if (state.has(key)) properties.add(new RecipeProperty(key,state.get(key).toString()));
            }
        } catch (RuntimeException malformed) {
            properties.add(new RecipeProperty("Runtime projection", "Some properties could not be interpreted; see the normalized payload."));
        }
        return new RecipeDefinition(entry.id(),new RecipeTypeDefinition(type),inputs,outputs,new RecipeLayout(),properties,source,
                state.toString(),RecipeParseStatus.PARTIALLY_PARSED,List.of());
    }

    private void slots(final JsonObject state, final String role, final List<IngredientDefinition> inputs, final List<RecipeOutput> outputs) {
        if (!state.has(role) || !state.get(role).isJsonObject()) return;
        for (final var capability : state.getAsJsonObject(role).entrySet()) {
            if (!capability.getValue().isJsonArray()) continue;
            final var key = capability.getKey().replaceFirst("^gtceu:", "");
            if (!key.equals("item") && !key.equals("fluid")) continue;
            for (final var slot : capability.getValue().getAsJsonArray()) {
                if (!slot.isJsonObject() || !slot.getAsJsonObject().has("content")) continue;
                final var value = ingredient(slot.getAsJsonObject().get("content"));
                if (role.equals("inputs")) inputs.add(value);
                else outputs.add(new RecipeOutput.Capability(key,value,slot.toString()));
            }
        }
    }

    private IngredientDefinition ingredient(final JsonElement value) {
        if (value.isJsonArray()) return new IngredientDefinition.Alternatives(value.getAsJsonArray().asList().stream().map(this::ingredient).toList());
        if (value.isJsonObject()) {
            final var object = value.getAsJsonObject();
            if (object.has("ingredient") && object.has("count") && object.get("count").getAsLong()>0)
                return new IngredientDefinition.Sized(ingredient(object.get("ingredient")),object.get("count").getAsLong(),value.toString());
            if (object.has("value") && object.has("amount") && object.get("amount").getAsLong()>0) {
                final var fluid=object.get("value");
                if (fluid.isJsonObject()) {
                    final var ref=fluid.getAsJsonObject();
                    if(ref.has("fluid") || ref.has("tag")) return new IngredientDefinition.Fluid(ResourceLocation.parse(ref.get(ref.has("fluid")?"fluid":"tag").getAsString()),
                            ref.has("tag"),object.get("amount").getAsLong(),value.toString());
                }
            }
            if (object.has("item")) return new IngredientDefinition.Item(ResourceLocation.parse(object.get("item").getAsString()));
            if (object.has("tag")) return new IngredientDefinition.Tag(ResourceLocation.parse(object.get("tag").getAsString()));
        }
        return new IngredientDefinition.Unknown(value.toString());
    }
}
