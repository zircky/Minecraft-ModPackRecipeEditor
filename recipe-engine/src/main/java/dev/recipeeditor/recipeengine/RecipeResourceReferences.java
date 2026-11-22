package dev.recipeeditor.recipeengine;
import dev.recipeeditor.registry.*;
import java.util.*;
/** Only already-parsed typed operands contribute static identities; arbitrary JSON keys do not. */
public final class RecipeResourceReferences {
    public List<ResourceReference> collect(final List<RecipeDefinition> recipes) {
        final var result=new ArrayList<ResourceReference>();
        for(final var recipe:recipes) {
            if(recipe.status()==RecipeParseStatus.INVALID||recipe.status()==RecipeParseStatus.UNSUPPORTED)continue;
            final var source=recipe.source();final var evidence=new ResourceSource(source.type(),source.modIds(),source.archivePath(),source.internalPath(),"Parsed recipe reference; registration unknown");
            recipe.inputs().forEach(input->collect(input,evidence,result));
            recipe.outputs().forEach(output->{if(output instanceof RecipeOutput.Fixed item)result.add(new ResourceReference(ResourceKind.ITEM,item.id(),evidence));else if(output instanceof RecipeOutput.Capability capability)collect(capability.ingredient(),evidence,result);});
        }
        return result.stream().distinct().toList();
    }
    private void collect(final IngredientDefinition value,final ResourceSource source,final List<ResourceReference> result) {
        if(value instanceof IngredientDefinition.Item item)result.add(new ResourceReference(ResourceKind.ITEM,item.id(),source));
        else if(value instanceof IngredientDefinition.Sized sized)collect(sized.ingredient(),source,result);
        else if(value instanceof IngredientDefinition.Alternatives alternatives)alternatives.choices().forEach(choice->collect(choice,source,result));
        else if(value instanceof IngredientDefinition.Fluid fluid&&!fluid.tag())result.add(new ResourceReference(ResourceKind.FLUID,fluid.id(),source));
    }
}
