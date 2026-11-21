package dev.recipeeditor.registry;

import java.util.*;
import static dev.recipeeditor.registry.ResourceVisual.Status.*;

/** Resolves explicit still/flowing sprite references only. PNG filenames never register fluids. */
public final class FluidTextureResolver {
    private final ResourceAssetCatalog assets;
    public FluidTextureResolver(final ResourceAssetCatalog assets) { this.assets=assets; }
    public ResourceVisual resolve(final Map<String,String> metadata) {
        final String still=metadata.getOrDefault("stillTexture","");
        final String reference=still.isBlank()?metadata.getOrDefault("flowingTexture",""):still;
        if(reference.isBlank()) return ResourceVisual.unavailable(RUNTIME_TEXTURE_REQUIRED,"Fluid has no explicit texture/sprite metadata; synchronize a client Companion");
        final var textures=assets.texture(reference);
        if(textures.size()>1)return ResourceVisual.unavailable(AMBIGUOUS_SOURCE,"Multiple physical textures; pack precedence unknown: "+reference);
        if(textures.isEmpty())return ResourceVisual.unavailable(TEXTURE_NOT_FOUND,"Sprite not found in indexed assets: "+reference);
        Integer tint=null;
        try { if(metadata.containsKey("tintColor")){if(!metadata.get("tintColor").matches("[a-fA-F0-9]{8}"))throw new NumberFormatException();tint=(int)Long.parseUnsignedLong(metadata.get("tintColor"),16);} }
        catch(NumberFormatException error) { return ResourceVisual.unavailable(INVALID_TEXTURE,"Invalid runtime tint color"); }
        try {return new ResourceVisual(TEXTURE_SAMPLE,List.of(new ResourceVisual.Layer(textures.getFirst(),tint,activeHash(metadata,reference))),"Fluid still/flowing sprite sample: "+reference+(tint==null?"; tint unknown":"; verified runtime tint"));}
        catch(IllegalArgumentException invalid){return ResourceVisual.unavailable(INVALID_TEXTURE,"Invalid active fluid texture hash");}
    }
    String activeHash(final Map<String,String> metadata,final String reference) {
        try { return new ResourceJsonReader().read(metadata.getOrDefault("textureHashes","{}")).path(reference).asText(""); }
        catch(Exception error) { return ""; }
    }
}
