package dev.recipeeditor.companionprotocol;

import com.google.gson.JsonObject;
import java.util.*;

/** Wire-only records. Never contain game objects or authentication credentials. */
public final class RuntimeData {
    private RuntimeData() {}
    public enum Kind { ITEM, BLOCK, FLUID, RECIPE_TYPE, RECIPE_SERIALIZER }
    public enum Support { SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED }
    public record Environment(String sessionId, String sourceInstance, String minecraftVersion, String loader,
            String loaderVersion, String companionVersion, long generation, String collectedAt) {
        public Environment { text(sessionId);text(sourceInstance);text(minecraftVersion);text(loader);text(loaderVersion);text(companionVersion);text(collectedAt);if(generation<0)throw new IllegalArgumentException("Invalid generation"); }
    }
    public record Registry(Kind kind, String id, String displayName, String runtimeClass,
            Map<String,String> metadata, Map<String,List<String>> stateProperties) {
        public Registry { Objects.requireNonNull(kind);location(id);text(runtimeClass);Objects.requireNonNull(displayName);metadata=Map.copyOf(metadata);final var copy=new TreeMap<String,List<String>>();stateProperties.forEach((k,v)->copy.put(k,List.copyOf(v)));stateProperties=Collections.unmodifiableMap(copy); }
    }
    public record Tag(Kind kind, String id, List<String> entries) {
        public Tag { if(kind!=Kind.ITEM&&kind!=Kind.BLOCK&&kind!=Kind.FLUID)throw new IllegalArgumentException("Invalid tag registry");location(id);entries=List.copyOf(entries);entries.forEach(RuntimeData::location); }
    }
    public record Recipe(String id, String type, String serializer, String runtimeClass, String adapter,
            Support status, JsonObject properties, String sourceInformation, List<String> diagnostics) {
        public Recipe { location(id);if(!"UNKNOWN".equals(type))location(type);if(!"UNKNOWN".equals(serializer))location(serializer);if(("UNKNOWN".equals(type)||"UNKNOWN".equals(serializer))&&status==Support.SUPPORTED)throw new IllegalArgumentException("Unknown recipe metadata cannot be fully supported");text(runtimeClass);text(adapter);Objects.requireNonNull(status);properties=Objects.requireNonNull(properties).deepCopy();text(sourceInformation);diagnostics=List.copyOf(diagnostics); }
        @Override public JsonObject properties() { return properties.deepCopy(); }
    }
    public record Mod(String id, String version, String name) {
        public Mod {text(id);text(version);text(name);}
    }
    public record Snapshot(int schemaVersion, String snapshotId, boolean complete, Environment environment,
            List<Registry> registries, List<Tag> tags, List<Recipe> recipes, List<Mod> mods, List<String> diagnostics) {
        public Snapshot {
            if(schemaVersion!=1)throw new IllegalArgumentException("Unknown snapshot schema");text(snapshotId);if(!snapshotId.matches("[a-zA-Z0-9-]{1,64}"))throw new IllegalArgumentException("Invalid snapshot ID");Objects.requireNonNull(environment);
            registries=List.copyOf(registries);tags=List.copyOf(tags);recipes=List.copyOf(recipes);mods=List.copyOf(mods);diagnostics=List.copyOf(diagnostics);
            if(registries.size()>200000||tags.size()>100000||recipes.size()>200000||mods.size()>4096)throw new IllegalArgumentException("Snapshot count limit");
            unique(registries.stream().map(r->r.kind()+":"+r.id()).toList());unique(tags.stream().map(t->t.kind()+":"+t.id()).toList());unique(recipes.stream().map(Recipe::id).toList());unique(mods.stream().map(Mod::id).toList());
        }
    }
    public static void location(final String value) { if(value==null||value.length()>1024||!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Invalid resource location"); }
    private static void text(final String value) {if(value==null||value.isBlank()||value.length()>8192)throw new IllegalArgumentException("Missing/oversized text field");}
    private static void unique(final List<String> keys) {if(new HashSet<>(keys).size()!=keys.size())throw new IllegalArgumentException("Duplicate snapshot keys");}
}
