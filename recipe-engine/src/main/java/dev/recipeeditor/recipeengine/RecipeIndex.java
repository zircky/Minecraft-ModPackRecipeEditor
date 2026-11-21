package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.util.*;

/** Immutable lookup tables preserve every conflicting source; tag-expanded matches are candidates. */
public final class RecipeIndex {
    private final List<RecipeDefinition> recipes;
    private final Map<ResourceLocation,List<RecipeDefinition>> byId=new HashMap<>(),byInput=new HashMap<>(),byOutput=new HashMap<>(),byType=new HashMap<>(),byTag=new HashMap<>();
    private final Map<String,List<RecipeDefinition>> byNamespace=new HashMap<>(),byMod=new HashMap<>();
    private final Map<ResourceLocation,List<RecipeDefinition>> fluidInputs=new HashMap<>(),fluidOutputs=new HashMap<>();
    private final TagIndex tags;
    public RecipeIndex(final List<RecipeDefinition> recipes,final TagIndex tags) {
        this.recipes=List.copyOf(recipes); this.tags=tags;
        for (final var recipe:recipes) {
            dev.recipeeditor.core.io.SafeFiles.checkCancelled();
            add(byId,recipe.id(),recipe); add(byNamespace,recipe.id().namespace(),recipe);
            recipe.type().location().ifPresent(type->add(byType,type,recipe));
            recipe.source().sourceModIds().forEach(mod->add(byMod,mod,recipe));
            if (recipe.status()==RecipeParseStatus.UNSUPPORTED || recipe.status()==RecipeParseStatus.INVALID) continue;
            final var items=new HashSet<ResourceLocation>(); final var tagIds=new HashSet<ResourceLocation>();
            recipe.inputs().forEach(input->collect(input,items,tagIds));
            items.forEach(id->add(byInput,id,recipe)); tagIds.forEach(id->add(byTag,id,recipe));
            final var produced=new HashSet<ResourceLocation>();
            recipe.outputs().forEach(output->{
                if(output instanceof RecipeOutput.Fixed fixed) produced.add(fixed.id());
                else if(output instanceof RecipeOutput.Capability capability && capability.capability().equals("item")) collect(capability.ingredient(),produced,new HashSet<>());
            });
            produced.forEach(id->add(byOutput,id,recipe));
            final var fluids=new HashSet<ResourceLocation>();recipe.inputs().forEach(input->collectFluids(input,fluids));
            fluids.forEach(id->add(fluidInputs,id,recipe));
            fluids.clear();recipe.outputs().forEach(output->{if(output instanceof RecipeOutput.Capability value&&value.capability().equals("fluid"))collectFluids(value.ingredient(),fluids);});
            fluids.forEach(id->add(fluidOutputs,id,recipe));
        }
    }
    private void collect(final IngredientDefinition ingredient,final Set<ResourceLocation> items,final Set<ResourceLocation> tagIds) {
        if (ingredient instanceof IngredientDefinition.Item item) items.add(item.id());
        else if (ingredient instanceof IngredientDefinition.Tag tag) { tagIds.add(tag.id()); items.addAll(tags.members(ResourceKind.ITEM,tag.id())); }
        else if (ingredient instanceof IngredientDefinition.Sized sized) collect(sized.ingredient(),items,tagIds);
        else if (ingredient instanceof IngredientDefinition.Alternatives alternatives) alternatives.choices().forEach(choice->collect(choice,items,tagIds));
    }
    private <K> void add(final Map<K,List<RecipeDefinition>> map,final K key,final RecipeDefinition recipe) { map.computeIfAbsent(key,k->new ArrayList<>()).add(recipe); }
    private void collectFluids(final IngredientDefinition value,final Set<ResourceLocation> fluids) {
        if(value instanceof IngredientDefinition.Fluid fluid) { if(fluid.tag())fluids.addAll(tags.members(ResourceKind.FLUID,fluid.id()));else fluids.add(fluid.id()); }
        else if(value instanceof IngredientDefinition.Sized sized)collectFluids(sized.ingredient(),fluids);
        else if(value instanceof IngredientDefinition.Alternatives alternatives)alternatives.choices().forEach(child->collectFluids(child,fluids));
    }
    public List<RecipeDefinition> findRecipesUsing(final ResourceKind kind,final ResourceLocation id) { return kind==ResourceKind.ITEM?findRecipesUsing(id):kind==ResourceKind.FLUID?List.copyOf(fluidInputs.getOrDefault(id,List.of())):List.of(); }
    public List<RecipeDefinition> findRecipesProducing(final ResourceKind kind,final ResourceLocation id) { return kind==ResourceKind.ITEM?findRecipesProducing(id):kind==ResourceKind.FLUID?List.copyOf(fluidOutputs.getOrDefault(id,List.of())):List.of(); }
    public TagIndex tagIndex() { return tags; }
    public List<RecipeDefinition> recipes() { return recipes; }
    public List<RecipeDefinition> findById(final ResourceLocation id) { return List.copyOf(byId.getOrDefault(id,List.of())); }
    public List<RecipeDefinition> findRecipesUsing(final ResourceLocation item) { return List.copyOf(byInput.getOrDefault(item,List.of())); }
    public List<RecipeDefinition> findRecipesProducing(final ResourceLocation item) { return List.copyOf(byOutput.getOrDefault(item,List.of())); }
    public List<RecipeDefinition> findRecipesByType(final ResourceLocation type) { return List.copyOf(byType.getOrDefault(type,List.of())); }
    public List<RecipeDefinition> findRecipesByTag(final ResourceLocation tag) { return List.copyOf(byTag.getOrDefault(tag,List.of())); }
    public List<RecipeDefinition> byNamespace(final String namespace) { return List.copyOf(byNamespace.getOrDefault(namespace,List.of())); }
    public List<RecipeDefinition> byMod(final String mod) { return List.copyOf(byMod.getOrDefault(mod,List.of())); }
    public List<RecipeDefinition> bySourceMod(final String mod) { return byMod(mod); }
    public RecipeProvenance provenance(final ResourceLocation id) {
        return new RecipeSourceResolver().discover(id,findById(id).stream().map(RecipeDefinition::source).toList());
    }
    public Map<ResourceLocation,List<RecipeDefinition>> conflicts() {
        final var result=new HashMap<ResourceLocation,List<RecipeDefinition>>();
        byId.forEach((id,values)->{ if(values.size()>1) result.put(id,List.copyOf(values)); }); return Map.copyOf(result);
    }
}
