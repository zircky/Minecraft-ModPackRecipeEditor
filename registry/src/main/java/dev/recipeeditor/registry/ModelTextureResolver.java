package dev.recipeeditor.registry;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
import static dev.recipeeditor.registry.ResourceVisual.Status.*;

/** Resolves flat layers or a labelled representative block texture, not game geometry. */
public final class ModelTextureResolver {
    private final ResourceAssetCatalog assets;
    private final ResourceJsonReader reader=new ResourceJsonReader();
    public ModelTextureResolver(final ResourceScanResult scan) { this(new ResourceAssetCatalog(scan)); }
    public ModelTextureResolver(final ResourceAssetCatalog assets) { this.assets=assets; }
    List<ResourceSource> resolve(final ResourceDocument document) {
        return preview(document).layers().stream().map(ResourceVisual.Layer::source).toList();
    }
    public ResourceVisual model(final String modelId) {
        try {
            final var documents=assets.models(ResourceLocation.parse(modelId));
            if (documents.size()>1) return missing(AMBIGUOUS_SOURCE,"Model override order is unknown: "+modelId);
            return documents.isEmpty()?missing(RUNTIME_TEXTURE_REQUIRED,"No static model: "+modelId):preview(documents.getFirst());
        } catch (IllegalArgumentException error) { return missing(INVALID_TEXTURE,"Invalid model reference"); }
    }
    public ResourceVisual preview(final ResourceDocument document) {
        try {
            final var json=reader.read(document.text());
            if (!json.isObject()) return missing(INVALID_TEXTURE,"Model must be an object");
            return document.source().internalPath().contains("/blockstates/")?blockstate(json):inherited(json);
        } catch (Exception error) { return missing(INVALID_TEXTURE,"Invalid model: "+error.getMessage()); }
    }
    private ResourceVisual blockstate(final JsonNode json) {
        final var references=new LinkedHashSet<String>();
        collectReferences(json.path("variants"),references);
        for (final var part:json.path("multipart")) collectReferences(part.path("apply"),references);
        if (references.isEmpty()) return missing(RENDERING_UNSUPPORTED,"No supported blockstate model reference");
        ResourceVisual representative=null;
        for (final var reference:references) {
            final var visual=model(reference);
            if (visual.status()==AMBIGUOUS_SOURCE) return visual;
            if (representative==null && !visual.layers().isEmpty()) representative=visual;
        }
        if (representative==null) return missing(RENDERING_UNSUPPORTED,"Blockstate requires a runtime renderer or missing assets");
        if (references.size()==1 && !json.has("multipart")) return representative;
        return new ResourceVisual(RENDERING_UNSUPPORTED,representative.layers(),"Representative texture from one variant/part; state geometry is not rendered");
    }
    private void collectReferences(final JsonNode node, final Set<String> references) {
        if (references.size()>128) throw new IllegalArgumentException("Blockstate reference limit");
        if (node.isObject() && node.path("model").isTextual()) references.add(node.path("model").asText());
        else if (node.isContainerNode()) for (final var child:node) collectReferences(child,references);
    }
    private ResourceVisual inherited(JsonNode model) throws java.io.IOException {
        final var values=new LinkedHashMap<String,String>();
        final var visited=new HashSet<ResourceLocation>();
        boolean complex=false,generated=false;
        for (int depth=0;depth<32;depth++) {
            complex|=model.has("elements") || model.has("overrides") || model.has("loader");
            final var fields=model.path("textures").fields();
            while (fields.hasNext()) { final var field=fields.next(); if(field.getValue().isTextual()) values.putIfAbsent(field.getKey(),field.getValue().asText()); }
            final String parent=model.path("parent").asText();
            if (parent.isEmpty()) break;
            final var id=ResourceLocation.parse(parent);
            if (!visited.add(id)) return missing(INVALID_TEXTURE,"Cyclic model inheritance");
            final var parents=assets.models(id);
            if (parents.size()>1) return missing(AMBIGUOUS_SOURCE,"Parent model override order is unknown: "+id);
            if (parents.isEmpty() && id.namespace().equals("minecraft") && Set.of("item/generated","item/handheld","item/handheld_rod","builtin/generated").contains(id.path())) { generated=true;break; }
            if (parents.isEmpty()) {
                complex|=!id.namespace().equals("minecraft") || !id.path().startsWith("block/cube");
                break;
            }
            model=reader.read(parents.getFirst().text());
            if (depth==31) return missing(INVALID_TEXTURE,"Model inheritance depth limit");
        }
        final var layers=new ArrayList<ResourceVisual.Layer>();
        if (generated) {
            if(values.keySet().stream().anyMatch(key->key.matches("layer[0-9]+")&&Integer.parseInt(key.substring(5))>=8))return missing(UNSUPPORTED_RENDERER,"Generated item exceeds eight preview layers");
            for (int i=0;i<8;i++) {
                final String key="layer"+i;
                if (!values.containsKey(key)) { if(i==0) return missing(TEXTURE_NOT_FOUND,"Missing layer0");break; }
                final var visual=texture(values,key);
                if (visual.layers().isEmpty()) return visual;
                layers.addAll(visual.layers());
            }
            return new ResourceVisual(complex?RENDERING_UNSUPPORTED:READY,layers,complex?"Base layers only; overrides/custom geometry are not rendered":"Generated item layers");
        }
        for (final String key:List.of("all","side","top","bottom","front","texture","particle")) {
            if (!values.containsKey(key)) continue;
            final var sample=texture(values,key);
            if (sample.layers().isEmpty()) return sample;
            return new ResourceVisual(complex || key.equals("particle")?RENDERING_UNSUPPORTED:TEXTURE_SAMPLE,sample.layers(),"Representative #"+key+" texture only; geometry is not rendered");
        }
        return missing(complex?UNSUPPORTED_RENDERER:RUNTIME_TEXTURE_REQUIRED,"No supported static reference; runtime renderer may supply one");
    }
    private ResourceVisual texture(final Map<String,String> values,final String key) {
        String value=values.getOrDefault(key,"");final var seen=new HashSet<String>();
        while(value.startsWith("#")) { if(!seen.add(value)) return missing(INVALID_TEXTURE,"Cyclic texture variable");value=values.getOrDefault(value.substring(1),""); }
        final var sources=assets.texture(value);
        if(sources.size()>1) return missing(AMBIGUOUS_SOURCE,"Texture override order is unknown: "+value);
        if(sources.isEmpty()) return missing(TEXTURE_NOT_FOUND,"Texture not found: "+value);
        return new ResourceVisual(TEXTURE_SAMPLE,List.of(new ResourceVisual.Layer(sources.getFirst(),null)),"Texture reference: "+value);
    }
    private ResourceVisual missing(final ResourceVisual.Status status,final String explanation) { return ResourceVisual.unavailable(status,explanation); }
}
