package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData.*;
import dev.recipeeditor.recipeengine.editor.RecipeFormService;
import java.util.*;

/** Runtime type inventory, including empty registered types. Capabilities describe snapshot-backed workflows. */
public final class RecipeTypeRegistry {
    private static final Set<String> VANILLA_TEMPLATES = Set.copyOf(new RecipeFormService().types());
    public enum Operation { VIEW, CREATE, EDIT, DELETE, DUPLICATE, EXPORT_JSON, EXPORT_KUBEJS, EXPORT_CRAFTTWEAKER }

    public record Descriptor(String type, boolean registered, Set<String> serializers, String namespace,
            String sourceMod, String namespaceModVersion, int recipes, Set<String> properties,
            Set<Operation> capabilities, String reason) {
        public Descriptor {
            serializers = Set.copyOf(serializers);
            properties = Set.copyOf(properties);
            capabilities = Set.copyOf(capabilities);
        }
    }

    public List<Descriptor> discover(final Snapshot snapshot) {
        if (!snapshot.complete()) throw new IllegalArgumentException("Incomplete runtime snapshot");
        final var types = new TreeMap<String, Evidence>();
        final var installed = new HashMap<String, String>();
        snapshot.mods().forEach(mod -> installed.put(mod.id(), mod.version()));
        snapshot.registries().stream().filter(row -> row.kind() == Kind.RECIPE_TYPE)
                .forEach(row -> types.computeIfAbsent(row.id(), ignored -> new Evidence()).registered = true);
        final boolean vanillaTarget = snapshot.environment().minecraftVersion().equals("1.20.1")
                && snapshot.environment().loader().equals("FORGE") && snapshot.environment().loaderVersion().startsWith("47.");
        final var drafts = new VanillaRuntimeDraft();
        for (final Recipe recipe : snapshot.recipes()) {
            if (recipe.type().equals("UNKNOWN")) continue;
            final var evidence = types.computeIfAbsent(recipe.type(), ignored -> new Evidence());
            evidence.count++;
            evidence.serializers.add(recipe.serializer());
            evidence.properties.addAll(recipe.properties().keySet());
            if (vanillaTarget && !evidence.duplicate && drafts.supports(recipe)) evidence.duplicate = true;
        }
        return types.entrySet().stream().map(entry -> {
            final String id = entry.getKey();
            final Evidence value = entry.getValue();
            final String namespace = id.substring(0, id.indexOf(':'));
            final var operations = EnumSet.of(Operation.VIEW);
            // A recipe manager type (e.g. crafting) can represent several unrelated serializers.
            // Only offer templates for exact, observed serializers, never from namespace or registry order.
            if (vanillaTarget && value.serializers.stream().anyMatch(serializer -> knownSerializer(id, serializer))
                    && namespace.equals("minecraft")) {
                operations.add(Operation.CREATE);
                operations.add(Operation.EXPORT_JSON);
            }
            if (value.duplicate) operations.add(Operation.DUPLICATE);
            final String reason = operations.contains(Operation.CREATE)
                    ? "Verified vanilla serializers support new project recipes. Runtime originals require editable source for update/delete."
                    : "Runtime evidence available. No verified authoring/export contract for the observed serializers.";
            return new Descriptor(id, value.registered, value.serializers, namespace, "UNKNOWN",
                    installed.getOrDefault(namespace, "UNKNOWN"), value.count, value.properties, operations, reason);
        }).toList();
    }

    public List<String> creationSerializers(final Descriptor descriptor) {
        if (!descriptor.capabilities().contains(Operation.CREATE)) return List.of();
        return descriptor.serializers().stream().filter(serializer -> knownSerializer(descriptor.type(), serializer)).sorted().toList();
    }

    private boolean knownSerializer(final String type, final String serializer) {
        if (!VANILLA_TEMPLATES.contains(serializer)) return false;
        final String managerType = switch (serializer) {
            case "minecraft:crafting_shaped", "minecraft:crafting_shapeless" -> "minecraft:crafting";
            case "minecraft:smithing_transform", "minecraft:smithing_trim" -> "minecraft:smithing";
            default -> serializer;
        };
        return type.equals(managerType);
    }

    private static final class Evidence {
        private final Set<String> serializers = new TreeSet<>();
        private final Set<String> properties = new TreeSet<>();
        private boolean registered;
        private boolean duplicate;
        private int count;
    }
}
