package dev.recipeeditor.exporters;

import dev.recipeeditor.core.ResourceLocation;
import java.util.Map;

/** New Minecraft layouts require an explicit version entry, not a guessed plural directory. */
public final class RecipeDataPathResolver {
    private final Map<String,String> directories=Map.of("1.20.1","recipes");
    public String resolve(final String minecraftVersion,final ResourceLocation id){
        final String directory=directories.get(minecraftVersion);
        if(directory==null)throw new IllegalArgumentException("Unsupported recipe data layout: "+minecraftVersion);
        return ExportPaths.relative("data/"+id.namespace()+"/"+directory+"/"+id.path()+".json");
    }
}
