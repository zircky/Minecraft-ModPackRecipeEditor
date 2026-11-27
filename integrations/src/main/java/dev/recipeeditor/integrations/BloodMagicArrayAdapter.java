package dev.recipeeditor.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.api.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.RecipeMachineLayout.*;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.util.*;

/** Inspection of the array serializer. Effect arrays do not yield the bedrock sentinel. */
final class BloodMagicArrayAdapter implements ModdedRecipeAdapter {
    private final ResourceLocation type=ResourceLocation.parse("bloodmagic:array");
    private final Set<String> effects=Set.of("bounce","day","movement","night","spike","updraft");

    @Override public Map<ResourceLocation,ModdedRecipeType> types(){
        final var names=Map.of("en_us","Alchemy Array","ru_ru","Алхимический массив");
        return Map.of(type,new ModdedRecipeType(type,names,names));
    }

    @Override public RecipeParseResult parse(final RecipeParseContext context){
        try {
            final var raw=new ResourceJsonReader().read(context.rawJson());
            final var inputs=List.of(ingredient(raw.path("baseinput")),ingredient(raw.path("addedinput")));
            final var stack=raw.path("output");
            final var item=ResourceLocation.parse(stack.path("item").asText());
            final boolean sentinel=item.toString().equals("minecraft:bedrock");
            final String effect=context.id().namespace().equals("bloodmagic")&&context.id().path().startsWith("array/")
                    &&effects.contains(context.id().path().substring(6))?context.id().path().substring(6):"Unknown";
            final List<RecipeOutput> outputs=sentinel?List.of(new RecipeOutput.Dynamic("Array effect: "+effect)):
                    List.of(new RecipeOutput.Fixed(item,stack.has("count")?stack.path("count").intValue():1,false,stack.toString()));
            final var recipe=new RecipeDefinition(context.id(),context.type(),inputs,outputs,new RecipeLayout(),
                    sentinel?List.of(new RecipeProperty("Array effect",effect)):List.of(),context.source(),context.rawJson(),
                    RecipeParseStatus.PARTIALLY_PARSED,List.of());
            return new RecipeParseResult(recipe,List.of());
        } catch (Exception invalid) {
            final var diagnostic=new dev.recipeeditor.core.importing.ImportDiagnostic(dev.recipeeditor.core.importing.ScanStatus.WARNING,
                    context.source().internalPath(),"Array payload cannot be interpreted: "+invalid.getMessage());
            final var recipe=new RecipeDefinition(context.id(),context.type(),List.of(),List.of(),new RecipeLayout(),List.of(),
                    context.source(),context.rawJson(),RecipeParseStatus.UNSUPPORTED,List.of(diagnostic));
            return new RecipeParseResult(recipe,List.of(diagnostic));
        }
    }

    private IngredientDefinition ingredient(final JsonNode node){
        if(node.isArray()&&!node.isEmpty()){
            final var values=new ArrayList<IngredientDefinition>();node.forEach(value->values.add(ingredient(value)));
            return new IngredientDefinition.Alternatives(values);
        }
        if(node.isObject()&&node.size()==1&&node.path("item").isTextual())return new IngredientDefinition.Item(ResourceLocation.parse(node.path("item").textValue()));
        if(node.isObject()&&node.size()==1&&node.path("tag").isTextual())return new IngredientDefinition.Tag(ResourceLocation.parse(node.path("tag").textValue()));
        return new IngredientDefinition.Unknown(node.toString());
    }

    @Override public RecipeMachineLayout layout(final RecipeDefinition recipe){
        try {
            final var root=new ResourceJsonReader().read(recipe.rawJson());
            final var groups=new ArrayList<Group>();
            groups.add(new Group("Ingredients",Direction.INPUT,List.of(new Slot(SlotKind.ITEM,"Base",root.path("baseinput").toString()),
                    new Slot(SlotKind.ITEM,"Added",root.path("addedinput").toString()))));
            if(recipe.outputs().stream().anyMatch(RecipeOutput.Fixed.class::isInstance))
                groups.add(new Group("Result",Direction.OUTPUT,List.of(new Slot(SlotKind.ITEM,"Result",root.path("output").toString()))));
            final var properties=new LinkedHashMap<String,String>();
            recipe.properties().forEach(value->properties.put(value.name(),value.rawValue()));
            return new RecipeMachineLayout(Style.LINEAR,groups,properties,Optional.empty());
        } catch(java.io.IOException invalid){throw new IllegalArgumentException(invalid);}
    }

    @Override public RecipeTypeCapabilities capabilities(final RecipeDefinition recipe){
        return new RecipeTypeCapabilities(recipe.status()==RecipeParseStatus.PARTIALLY_PARSED
                ?Set.of(RecipeTypeCapabilities.Feature.CAN_VIEW):Set.of(),"Alchemy Array serializer is supported for inspection");
    }
}
