package dev.recipeeditor.integrations.api;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.util.*;

/** Exact serializer keys and fail-fast duplicate detection, never first-match namespace ownership. */
public final class ModdedRecipeAdapterRegistry implements RecipeParser {
    private final Map<ResourceLocation,ModdedRecipeAdapter> adapters;
    private final List<ModdedRecipeType> types;
    public ModdedRecipeAdapterRegistry(final List<ModdedRecipeAdapter> registered) {
        final var index=new LinkedHashMap<ResourceLocation,ModdedRecipeAdapter>();
        final var descriptors=new ArrayList<ModdedRecipeType>();
        for(final var adapter:registered)adapter.types().forEach((id,type)->{
            if(!id.equals(type.id()))throw new IllegalArgumentException("Adapter descriptor key mismatch: "+id);
            if(index.putIfAbsent(id,adapter)!=null)throw new IllegalArgumentException("Duplicate recipe adapter: "+id);
            descriptors.add(type);
        });
        adapters=Map.copyOf(index);types=descriptors.stream().sorted(Comparator.comparing(type->type.id().toString())).toList();
    }
    public Optional<ModdedRecipeAdapter> find(final ResourceLocation type){return Optional.ofNullable(adapters.get(type));}
    public List<ModdedRecipeType> types(){return types;}
    @Override public boolean supports(final ResourceLocation type){return adapters.containsKey(type);}
    @Override public RecipeParseResult parse(final RecipeParseContext context){return find(context.type().location().orElseThrow()).orElseThrow(()->new IllegalArgumentException("No adapter: "+context.type().displayId())).parse(context);}
}
