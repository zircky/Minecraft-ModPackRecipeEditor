package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.util.*;

/** Keeps both provenances. Normalized runtime properties never become fictional source JSON. */
public final class RecipeMergeService {
    public record Entry(ResourceLocation id,List<RecipeDefinition> staticDefinitions,RuntimeData.Recipe runtime,List<String> diagnostics,RecipeProvenance provenance) {public Entry{staticDefinitions=List.copyOf(staticDefinitions);diagnostics=List.copyOf(diagnostics);Objects.requireNonNull(provenance);}}
    public record Result(List<Entry> entries,Set<ResourceLocation> changedOrMissing,List<String> diagnostics) {public Result{entries=List.copyOf(entries);changedOrMissing=Set.copyOf(changedOrMissing);diagnostics=List.copyOf(diagnostics);}}
    public Result merge(final RecipeIndex originals,final RuntimeData.Snapshot snapshot) {
        if(!snapshot.complete())throw new IllegalArgumentException("Partial runtime snapshot");final var runtime=new LinkedHashMap<ResourceLocation,RuntimeData.Recipe>();snapshot.recipes().forEach(recipe->runtime.put(ResourceLocation.parse(recipe.id()),recipe));
        final var ids=new TreeSet<ResourceLocation>(runtime.keySet());originals.recipes().forEach(recipe->ids.add(recipe.id()));final var entries=new ArrayList<Entry>();final var changed=new HashSet<ResourceLocation>();final var messages=new ArrayList<String>();
        for(final var id:ids) {
            final var staticDefinitions=originals.findById(id);final var actual=runtime.get(id);final var diagnostics=new ArrayList<String>();
            if(actual==null&&!staticDefinitions.isEmpty()){if(staticDefinitions.stream().allMatch(r->r.status()==RecipeParseStatus.PARSED))changed.add(id);diagnostics.add("Static definition absent from this complete runtime collection (unknown adapter coverage may still limit dynamic discovery)");}
            if(actual!=null&&!staticDefinitions.isEmpty()) {
                if(staticDefinitions.stream().noneMatch(base->matchesKnownFields(base,actual))){changed.add(id);diagnostics.add("Static/runtime known recipe properties differ");}
                else diagnostics.add("Matching ID/available properties; original JSON equivalence is not proven");
            }
            if(actual!=null&&actual.status()!=RuntimeData.Support.SUPPORTED)diagnostics.add("Runtime view is partial/unsupported; no editable source JSON is inferred");
            final var sources=staticDefinitions.stream().map(RecipeDefinition::source).toList();
            final var resolver=new RecipeSourceResolver();
            final var provenance=actual==null?resolver.discover(id,sources):resolver.runtime(
                    new RuntimeRecipeObservation(id,actual.type(),actual.serializer(),snapshot.snapshotId(),snapshot.environment().collectedAt(),actual.sourceInformation()),sources,
                    new RecipeSource(dev.recipeeditor.registry.SourceType.RUNTIME,List.of(),java.nio.file.Path.of(snapshot.environment().sourceInstance()),"",
                            "Companion snapshot "+snapshot.snapshotId()+" @ "+snapshot.environment().collectedAt()+"; "+actual.sourceInformation()));
            diagnostics.forEach(message->messages.add(id+": "+message));entries.add(new Entry(id,staticDefinitions,actual,diagnostics,provenance));
        }
        return new Result(entries,changed,messages);
    }
    private boolean conflictingLeaves(final com.google.gson.JsonElement expected,final com.google.gson.JsonElement actual) {
        if(expected.isJsonObject()&&actual.isJsonObject()){for(final var entry:expected.getAsJsonObject().entrySet())if(actual.getAsJsonObject().has(entry.getKey())&&conflictingLeaves(entry.getValue(),actual.getAsJsonObject().get(entry.getKey())))return true;return false;}
        if(expected.isJsonArray()&&actual.isJsonArray()){final var a=expected.getAsJsonArray();final var b=actual.getAsJsonArray();if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(conflictingLeaves(a.get(i),b.get(i)))return true;return false;}
        if(expected.isJsonPrimitive()&&actual.isJsonPrimitive())return !expected.equals(actual);
        return false; // Different normalized shapes do not prove a semantic conflict.
    }
    private boolean matchesKnownFields(final RecipeDefinition base,final RuntimeData.Recipe runtime) {
        if(runtime.type().equals("UNKNOWN")||runtime.serializer().equals("UNKNOWN"))return true;
        // JSON type is commonly a serializer ID; GT machine JSON may name its recipe type.
        if(!base.type().displayId().equals(runtime.serializer())&&!base.type().displayId().equals(runtime.type()))return false;
        try {
            final var raw=ProtocolCodec.parse(base.rawJson().getBytes(java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();final var actual=runtime.properties();
            for(final var mapping:Map.of("group","group","experience","experience","cookingtime","cookingTime").entrySet())if(raw.has(mapping.getKey())&&actual.has(mapping.getValue())&&!raw.get(mapping.getKey()).equals(actual.get(mapping.getValue())))return false;
            if(actual.has("ingredients")) {
                final var expected=new com.google.gson.JsonArray();
                if(raw.has("pattern")&&raw.has("key")) {for(final var row:raw.getAsJsonArray("pattern"))for(final char symbol:row.getAsString().toCharArray())expected.add(symbol==' '?new com.google.gson.JsonArray():raw.getAsJsonObject("key").get(String.valueOf(symbol)));}
                else if(raw.has("ingredients"))raw.getAsJsonArray("ingredients").forEach(expected::add);
                else if(raw.has("ingredient"))expected.add(raw.get("ingredient"));
                if(!expected.isEmpty()&&!expected.equals(actual.get("ingredients")))return false;
            }
            if(runtime.adapter().equals("gtceu-7.1.4"))for(final var key:List.of("inputs","outputs","tickInputs","tickOutputs","duration","recipeConditions","data"))if(raw.has(key)&&actual.has(key)&&conflictingLeaves(raw.get(key),actual.get(key)))return false;
            if(raw.has("result")&&actual.has("result")) {final var expected=raw.get("result");final var observed=actual.getAsJsonObject("result");final String item=expected.isJsonPrimitive()?expected.getAsString():expected.getAsJsonObject().get("item").getAsString();if(observed.has("item")&&!item.equals(observed.get("item").getAsString()))return false;final int count=expected.isJsonObject()&&expected.getAsJsonObject().has("count")?expected.getAsJsonObject().get("count").getAsInt():raw.has("count")?raw.get("count").getAsInt():1;if(observed.has("count")&&count!=observed.get("count").getAsInt())return false;}
            return true;
        }catch(Exception partial){return true;}
    }
}
