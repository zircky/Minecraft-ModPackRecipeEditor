package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Resolves a texture sample only for simple generated items / cube blocks, never arbitrary geometry. */
final class ModelTextureResolver {
    private final Map<ResourceLocation,List<ResourceDocument>> models=new HashMap<>();
    private final Map<String,List<ResourceSource>> textures=new HashMap<>();
    private final ResourceJsonReader reader=new ResourceJsonReader();
    ModelTextureResolver(final ResourceScanResult scan) {
        for (final var document:scan.documents()) {
            final String path=document.source().internalPath();
            if (!path.matches("assets/[^/]+/models/(item|block)/.+\\.json")) continue;
            final String[] parts=path.split("/",4);
            final var id=new ResourceLocation(parts[1],parts[3].substring(0,parts[3].length()-5));
            models.computeIfAbsent(id,k->new ArrayList<>()).add(document);
        }
        scan.textures().forEach(source->textures.computeIfAbsent(source.internalPath(),k->new ArrayList<>()).add(source));
    }
    List<ResourceSource> resolve(final ResourceDocument document) {
        try {
            JsonNode model=reader.read(document.text());
            final String path=document.source().internalPath();
            if (path.contains("/blockstates/")) {
                if (model.has("multipart") || !model.path("variants").isObject()) return List.of();
                final var refs=new HashSet<String>();
                for (final var variant:model.path("variants")) {
                    if (variant.isArray()) return List.of();
                    if (!variant.path("model").isTextual()) return List.of();
                    refs.add(variant.path("model").asText());
                }
                if (refs.size()!=1) return List.of();
                final var docs=models.getOrDefault(ResourceLocation.parse(refs.iterator().next()),List.of());
                return docs.size()==1?resolve(docs.getFirst()):List.of();
            }
            final var textureValues=new HashMap<String,String>(); final var visited=new HashSet<ResourceLocation>();
            String terminal="";
            for (int depth=0;depth<16;depth++) {
                if (model.has("elements") || model.has("overrides") || model.has("loader")) return List.of();
                final var fields=model.path("textures").fields();
                while(fields.hasNext()) { final var field=fields.next(); if (field.getValue().isTextual()) textureValues.putIfAbsent(field.getKey(),field.getValue().asText()); }
                terminal=model.path("parent").asText();
                if (Set.of("minecraft:item/generated","item/generated","minecraft:item/handheld","item/handheld","minecraft:block/cube_all","block/cube_all").contains(terminal)) break;
                if (terminal.isEmpty()) return List.of();
                final var parent=ResourceLocation.parse(terminal);
                if (!visited.add(parent)) return List.of();
                final var docs=models.getOrDefault(parent,List.of());
                if (docs.size()!=1) return List.of();
                model=reader.read(docs.getFirst().text());
            }
            if (!Set.of("minecraft:item/generated","item/generated","minecraft:item/handheld","item/handheld","minecraft:block/cube_all","block/cube_all").contains(terminal)) return List.of();
            final String key=terminal.contains("cube_all")?"all":"layer0";
            if (!terminal.contains("cube_all") && textureValues.keySet().stream().anyMatch(k->k.startsWith("layer")&&!k.equals("layer0"))) return List.of();
            String value=textureValues.getOrDefault(key,"");
            final var seen=new HashSet<String>();
            while (value.startsWith("#")) { if (!seen.add(value)) return List.of(); value=textureValues.getOrDefault(value.substring(1),""); }
            if (value.isEmpty()) return List.of();
            final var texture=ResourceLocation.parse(value);
            final var sources=textures.getOrDefault("assets/"+texture.namespace()+"/textures/"+texture.path()+".png",List.of());
            return sources.size()==1?List.copyOf(sources):List.of();
        } catch (Exception error) { return List.of(); }
    }
}
