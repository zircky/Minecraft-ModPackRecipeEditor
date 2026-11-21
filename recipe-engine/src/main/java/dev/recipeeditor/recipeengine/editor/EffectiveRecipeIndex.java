package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import java.util.*;

/** Incremental delta indexes; an edit never copies or reindexes the imported recipe catalogue. */
public final class EffectiveRecipeIndex {
    private RecipeIndex original;
    private final Map<ResourceLocation,RecipeDefinition> replacements=new LinkedHashMap<>();
    private final Set<ResourceLocation> hidden=new HashSet<>();
    private final Map<ResourceLocation,Set<ResourceLocation>> inputs=new HashMap<>(),outputs=new HashMap<>(),types=new HashMap<>(),tags=new HashMap<>();
    public EffectiveRecipeIndex(final RecipeIndex original) { this.original=Objects.requireNonNull(original); }
    synchronized void updateTags(final TagIndex runtimeTags) {
        original=new RecipeIndex(original.recipes(),runtimeTags);inputs.clear();outputs.clear();types.clear();tags.clear();replacements.values().forEach(recipe->index(recipe,true));
    }
    synchronized void apply(final ResourceLocation id,final RecipeChange change) {
        final var previous=replacements.remove(id); if(previous!=null) index(previous,false);
        hidden.remove(id);
        if(change==null) return;
        hidden.add(id);
        if(!change.replacement().isEmpty()) { final var recipe=change.replacement().getFirst(); replacements.put(id,recipe); index(recipe,true); }
    }
    private void index(final RecipeDefinition recipe,final boolean add) {
        recipe.type().location().ifPresent(type->put(types,type,recipe.id(),add));
        if(recipe.status()==RecipeParseStatus.UNSUPPORTED || recipe.status()==RecipeParseStatus.INVALID) return;
        final var items=new HashSet<ResourceLocation>(); final var tagIds=new HashSet<ResourceLocation>();
        recipe.inputs().forEach(input->collect(input,items,tagIds));
        items.forEach(item->put(inputs,item,recipe.id(),add)); tagIds.forEach(tag->put(tags,tag,recipe.id(),add));
        final var produced=new HashSet<ResourceLocation>();
        recipe.outputs().forEach(output->{
            if(output instanceof RecipeOutput.Fixed fixed) produced.add(fixed.id());
            else if(output instanceof RecipeOutput.Capability capability && capability.capability().equals("item")) collect(capability.ingredient(),produced,new HashSet<>());
        });
        produced.forEach(id->put(outputs,id,recipe.id(),add));
    }
    private void collect(final IngredientDefinition value,final Set<ResourceLocation> items,final Set<ResourceLocation> tagIds) {
        if(value instanceof IngredientDefinition.Item item) items.add(item.id());
        else if(value instanceof IngredientDefinition.Tag tag) { tagIds.add(tag.id()); items.addAll(original.tagIndex().members(ResourceKind.ITEM,tag.id())); }
        else if(value instanceof IngredientDefinition.Sized sized) collect(sized.ingredient(),items,tagIds);
        else if(value instanceof IngredientDefinition.Alternatives alternatives) alternatives.choices().forEach(choice->collect(choice,items,tagIds));
    }
    private void put(final Map<ResourceLocation,Set<ResourceLocation>> map,final ResourceLocation key,final ResourceLocation id,final boolean add) {
        if(add) map.computeIfAbsent(key,k->new LinkedHashSet<>()).add(id);
        else if(map.containsKey(key)) { map.get(key).remove(id); if(map.get(key).isEmpty()) map.remove(key); }
    }
    private List<RecipeDefinition> merge(final List<RecipeDefinition> base,final Set<ResourceLocation> ids) {
        final var values=new ArrayList<RecipeDefinition>(); base.stream().filter(r->!hidden.contains(r.id())).forEach(values::add);
        ids.forEach(id->{ final var recipe=replacements.get(id); if(recipe!=null) values.add(recipe); }); return List.copyOf(values);
    }
    public synchronized List<RecipeDefinition> findById(final ResourceLocation id) { return hidden.contains(id)?replacements.containsKey(id)?List.of(replacements.get(id)):List.of():original.findById(id); }
    public synchronized List<RecipeDefinition> findRecipesUsing(final ResourceLocation id) { return merge(original.findRecipesUsing(id),inputs.getOrDefault(id,Set.of())); }
    public synchronized List<RecipeDefinition> findRecipesProducing(final ResourceLocation id) { return merge(original.findRecipesProducing(id),outputs.getOrDefault(id,Set.of())); }
    public synchronized List<RecipeDefinition> findRecipesByType(final ResourceLocation id) { return merge(original.findRecipesByType(id),types.getOrDefault(id,Set.of())); }
    public synchronized List<RecipeDefinition> findRecipesByTag(final ResourceLocation id) { return merge(original.findRecipesByTag(id),tags.getOrDefault(id,Set.of())); }
    public synchronized List<RecipeDefinition> recipes() { return merge(original.recipes(),replacements.keySet()); }
    public synchronized List<RecipeDefinition> findRecipesUsing(final ResourceKind kind,final ResourceLocation id) {
        if(kind==ResourceKind.ITEM)return findRecipesUsing(id);
        if(kind!=ResourceKind.FLUID)return List.of();
        return merge(original.findRecipesUsing(kind,id),fluidReplacements(id,true));
    }
    public synchronized List<RecipeDefinition> findRecipesProducing(final ResourceKind kind,final ResourceLocation id) {
        if(kind==ResourceKind.ITEM)return findRecipesProducing(id);
        if(kind!=ResourceKind.FLUID)return List.of();
        return merge(original.findRecipesProducing(kind,id),fluidReplacements(id,false));
    }
    private Set<ResourceLocation> fluidReplacements(final ResourceLocation id,final boolean uses) {
        final var delta=new RecipeIndex(List.copyOf(replacements.values()),original.tagIndex());
        return (uses?delta.findRecipesUsing(ResourceKind.FLUID,id):delta.findRecipesProducing(ResourceKind.FLUID,id)).stream().map(RecipeDefinition::id).collect(java.util.stream.Collectors.toSet());
    }
}
