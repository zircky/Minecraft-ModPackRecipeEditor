package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import java.util.*;

/** Presentation comes from declared domain outputs, never from spelling the Recipe ID. */
public final class RecipeDisplayNameResolver {
    public record Output(ResourceKind kind,ResourceLocation id,long amount) { }
    public record Display(String name,String type,String machine,Optional<Output> previewOutput,int additionalOutputs) { }
    private final RegistryIndex registry;
    private final RecipeTypeDisplayNameResolver types;
    private final ResourceBrowserViewModel resources;
    private final Map<String,String> machines;
    public RecipeDisplayNameResolver(final RegistryIndex registry,final RecipeTypeDisplayNameResolver types){this(registry,types,Map.of());}
    public RecipeDisplayNameResolver(final RegistryIndex registry, final RecipeTypeDisplayNameResolver types, final Map<String,String> machines) {
        this.registry=registry;this.types=types;this.machines=Map.copyOf(machines);resources=new ResourceBrowserViewModel(registry);
    }
    public Display resolve(final RecipeDefinition recipe,final String language) {
        if(recipe.outputs().size()==1&&recipe.outputs().getFirst() instanceof RecipeOutput.Brew value)
            return new Display((language.equals("ru_ru")?"Напиток: ":"Brew: ")+value.definition().displayName(language),types.name(recipe.type().displayId(),language),types.machine(recipe.type().displayId(),language),Optional.empty(),0);
        final var previews=recipe.outputs().stream().map(this::reference).flatMap(Optional::stream).toList();
        final var output=previews.stream().filter(value->value.kind()==ResourceKind.ITEM).findFirst()
                .or(()->previews.stream().findFirst());
        final String name=output.map(value->registry.find(value.id()).stream().filter(entry->entry.kind()==value.kind()).findFirst()
                .map(entry->resources.displayName(entry,language)).orElse(value.id().toString()))
                .orElseGet(()->fallback(recipe,language));
        final String type=recipe.type().displayId();
        return new Display(name,types.name(type,language),machines.getOrDefault(type,types.machine(type,language)),output,Math.max(0,recipe.outputs().size()-1));
    }
    private String fallback(final RecipeDefinition recipe,final String language) {
        if(recipe.source().type()==SourceType.RUNTIME)return types.name(recipe.type().displayId(),language);
        final boolean ru=language.equals("ru_ru");
        return recipe.outputs().stream().anyMatch(RecipeOutput.Dynamic.class::isInstance)
                ? ru?"Динамический результат":"Dynamic result" : ru?"Неизвестный результат":"Unknown result";
    }
    private Optional<Output> reference(final RecipeOutput value) {
        if(value instanceof RecipeOutput.Fixed fixed)return Optional.of(new Output(ResourceKind.ITEM,fixed.id(),fixed.count()));
        if(value instanceof RecipeOutput.Capability capability) {
            final var kind=capability.capability().equals("item")?ResourceKind.ITEM:capability.capability().equals("fluid")?ResourceKind.FLUID:null;
            if(kind==null)return Optional.empty();
            var ingredient=capability.ingredient();long amount=1;
            if(ingredient instanceof IngredientDefinition.Sized sized){ingredient=sized.ingredient();amount=sized.amount();}
            if(ingredient instanceof IngredientDefinition.Item item)return Optional.of(new Output(kind,item.id(),amount));
            if(ingredient instanceof IngredientDefinition.Fluid fluid&&!fluid.tag())return Optional.of(new Output(kind,fluid.id(),fluid.amount()));
        }
        return Optional.empty();
    }
}
