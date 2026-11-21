package dev.recipeeditor.integrations.botania;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.RecipeMachineLayout.*;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.util.*;

/** Actual ingredient counts; reagent and block-state catalyst keep their distinct roles. */
public final class BotaniaRecipeLayoutProvider {
    public RecipeMachineLayout create(final RecipeDefinition recipe) {
        if(recipe.status()==RecipeParseStatus.INVALID||recipe.status()==RecipeParseStatus.UNSUPPORTED)throw new IllegalArgumentException("Cannot lay out an invalid recipe");
        try {
            final var root=new ResourceJsonReader().read(recipe.rawJson());final String type=recipe.type().displayId();
            final boolean infusion=type.equals("botania:mana_infusion"),brew=type.equals("botania:brew");
            final var inputs=new ArrayList<Slot>();
            if(infusion)inputs.add(new Slot(SlotKind.ITEM,"Input",root.path("input").toString()));
            else root.path("ingredients").forEach(node->inputs.add(new Slot(SlotKind.ITEM,"Ingredient",node.toString())));
            final var groups=new ArrayList<Group>();groups.add(new Group("Ingredients",Direction.INPUT,inputs));
            final var properties=new LinkedHashMap<String,String>();
            if(!brew)groups.add(new Group("Result",Direction.OUTPUT,List.of(new Slot(SlotKind.ITEM,"Output",root.path("output").toString()))));
            if(root.has("reagent"))groups.add(new Group("Reagent",Direction.AUXILIARY,List.of(new Slot(SlotKind.ITEM,"Reagent",root.path("reagent").toString()))));
            if(root.has("catalyst"))groups.add(new Group("Block-state catalyst",Direction.AUXILIARY,List.of(new Slot(SlotKind.BLOCK,"Catalyst",root.path("catalyst").toString()))));
            if(infusion)properties.put("Mana",root.path("mana").toString());
            recipe.properties().stream().filter(p->!Set.of("mana","reagent","catalyst").contains(p.name())).forEach(p->properties.put(p.name(),p.rawValue()));
            final var result=recipe.outputs().stream().filter(RecipeOutput.Brew.class::isInstance).map(RecipeOutput.Brew.class::cast).map(RecipeOutput.Brew::definition).findFirst();
            return new RecipeMachineLayout(brew?Style.BREWING:infusion?Style.LINEAR:Style.BATCH,groups,properties,result);
        }catch(java.io.IOException error){throw new IllegalArgumentException("Invalid recipe JSON",error);}
    }
}
