package dev.recipeeditor.recipeengine;

import java.util.*;
public final class RecipeSearchService {
    private final RecipeIndex index;
    public RecipeSearchService(final RecipeIndex index) { this.index=Objects.requireNonNull(index); }
    public List<RecipeDefinition> search(final String query,final String modId,final String type) {
        final String text=query.toLowerCase(Locale.ROOT).strip();
        final var candidates=!modId.isEmpty()?index.byMod(modId):!type.isEmpty()?index.findRecipesByType(dev.recipeeditor.core.ResourceLocation.parse(type)):index.recipes();
        return candidates.stream().filter(r->type.isEmpty() || r.type().displayId().equals(type)).filter(r->r.id().toString().contains(text)
                || r.type().rawId().contains(text) || r.source().modIds().stream().anyMatch(id->id.contains(text))).toList();
    }
}
