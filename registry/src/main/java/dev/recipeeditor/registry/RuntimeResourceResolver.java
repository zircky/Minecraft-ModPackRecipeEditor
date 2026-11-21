package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
import static dev.recipeeditor.registry.ResourceVisual.Status.*;

/** Reuses the static asset catalog for actual runtime model/sprite metadata, including generated resources. */
public final class RuntimeResourceResolver {
    private final ResourceAssetCatalog assets;
    private final ModelTextureResolver models;
    private final LocalizationService localization;
    public RuntimeResourceResolver(final ResourceScanResult scan) {
        assets=new ResourceAssetCatalog(scan);models=new ModelTextureResolver(assets);localization=new LocalizationService(scan.documents());
    }
    public LocalizationService localization() { return localization; }
    public ResourceVisual visual(final ResourceKind kind,final ResourceLocation id,final Map<String,String> metadata,final RegistryEntry original) {
        if(kind==ResourceKind.FLUID)return new FluidTextureResolver(assets).resolve(metadata);
        if(metadata.containsKey("previewSprites")) {
            try {
                final var layers=new ArrayList<ResourceVisual.Layer>();
                final var sprites=new ResourceJsonReader().read(metadata.get("previewSprites"));
                if(!sprites.isArray()||sprites.size()>8)return ResourceVisual.unavailable(UNSUPPORTED_RENDERER,"Runtime model exceeds preview layer limit");
                for(final var sprite:sprites) {
                    final var sources=assets.texture(sprite.path("sprite").asText());
                    if(sources.size()!=1)return ResourceVisual.unavailable(sources.isEmpty()?TEXTURE_NOT_FOUND:AMBIGUOUS_SOURCE,"Runtime sprite missing or ambiguous: "+sprite.path("sprite").asText());
                    layers.add(new ResourceVisual.Layer(sources.getFirst(),sprite.has("tint")?(int)Long.parseUnsignedLong(sprite.path("tint").asText(),16):null,new FluidTextureResolver(assets).activeHash(metadata,sprite.path("sprite").asText())));
                    if(!metadata.getOrDefault("previewMode","SAMPLE").equals("LAYERS"))break;
                }
                if(!layers.isEmpty())return new ResourceVisual(metadata.getOrDefault("previewMode","SAMPLE").equals("LAYERS")?READY:RENDERING_UNSUPPORTED,layers,"Runtime baked-model texture preview; geometry/overrides are not rendered");
            } catch(Exception error) { return ResourceVisual.unavailable(INVALID_TEXTURE,"Invalid runtime sprite metadata"); }
        }
        if(original!=null&&!original.visual().layers().isEmpty())return original.visual();
        return models.model(id.namespace()+":"+(kind==ResourceKind.ITEM?"item/":"block/")+id.path());
    }
}
