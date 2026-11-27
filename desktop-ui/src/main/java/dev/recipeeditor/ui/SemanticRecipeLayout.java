package dev.recipeeditor.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.RecipeMachineLayout.*;
import java.util.*;

/** Generic slot layout for recipes exposing typed inputs/outputs without a registered workstation layout. */
final class SemanticRecipeLayout {
    Optional<RecipeLayoutModel> resolve(final RecipeDefinition recipe) {
        if(recipe.inputs().isEmpty()&&recipe.outputs().isEmpty()&&recipe.properties().isEmpty())return Optional.empty();
        final var inputs=new ArrayList<Slot>();
        final var outputs=new ArrayList<Slot>();
        final var properties=new LinkedHashMap<String,String>();
        recipe.inputs().forEach(input->inputs.add(slot(input,"Input")));
        final boolean dynamic=recipe.outputs().stream().anyMatch(RecipeOutput.Dynamic.class::isInstance);
        for(final var output:recipe.outputs()) {
            if(output instanceof RecipeOutput.Fixed fixed) {
                final var value=new JsonObject();
                value.addProperty("item",fixed.id().toString());
                value.addProperty("count",fixed.count());
                final String nbt=RecipeSlotContent.parse(fixed.rawDetails()).nbt();
                if(!nbt.isBlank())value.addProperty("nbt",nbt);
                outputs.add(new Slot(SlotKind.ITEM,"Result",value.toString()));
            } else if(output instanceof RecipeOutput.Capability capability) {
                outputs.add(slot(capability.ingredient(),capability.capability()+" output"));
                chance(properties,capability.rawDetails(),outputs.size());
            }
        }
        if(dynamic)properties.put("Output","Depends on input stacks");
        for(final var field:recipe.properties()) {
            if(!field.name().equals("gregtech"))properties.put(field.name(),field.rawValue());
        }
        if(recipe.type().displayId().startsWith("gtceu:")) {
            try {
                final var model=new dev.recipeeditor.integrations.gregtech.GregTechRecipeMapper().map(recipe);
                model.duration().ifPresent(value->properties.put("Duration (ticks)",Integer.toString(value)));
                model.inputEUt().ifPresent(value->properties.put("EU/t input",Long.toString(value)));
                model.outputEUt().ifPresent(value->properties.put("EU/t output",Long.toString(value)));
                model.contents().stream().filter(dev.recipeeditor.integrations.gregtech.GregTechContent::nonConsumable)
                        .forEach(content->properties.merge("Non-consumable",json(content.ingredient()).toString(),(a,b)->a+", "+b));
                model.conditions().forEach(condition->properties.put("Condition "+condition.type(),condition.rawJson()));
            } catch(RuntimeException unknownCodec) { /* The observed typed slots remain available. */ }
        }
        final var groups=new ArrayList<Group>();
        group(groups,inputs,Direction.INPUT,"Inputs");
        group(groups,outputs,Direction.OUTPUT,"Outputs");
        return Optional.of(new RecipeLayoutModel(recipe.type().displayId(),RecipeLayoutProvider.Kind.PROCESSING,List.of(),
                RecipeSlotContent.parse(""),dynamic,Map.of(),Optional.of(new RecipeMachineLayout(Style.BATCH,groups,properties,Optional.empty()))));
    }

    private void group(final List<Group> groups,final List<Slot> slots,final Direction direction,final String title) {
        final var items=slots.stream().filter(slot->slot.kind()!=SlotKind.FLUID).toList();
        final var fluids=slots.stream().filter(slot->slot.kind()==SlotKind.FLUID).toList();
        if(!items.isEmpty())groups.add(new Group(title,direction,items));
        if(!fluids.isEmpty())groups.add(new Group("Fluid "+title.toLowerCase(Locale.ROOT),direction,fluids));
    }

    private Slot slot(final IngredientDefinition ingredient,final String role) {
        final var value=ingredient instanceof IngredientDefinition.Sized sized?sized.ingredient():ingredient;
        return new Slot(value instanceof IngredientDefinition.Fluid?SlotKind.FLUID:SlotKind.ITEM,role,json(ingredient).toString());
    }

    private JsonElement json(final IngredientDefinition ingredient) {
        final var node=new JsonObject();
        if(ingredient instanceof IngredientDefinition.Item item)node.addProperty("item",item.id().toString());
        else if(ingredient instanceof IngredientDefinition.Tag tag)node.addProperty("tag",tag.id().toString());
        else if(ingredient instanceof IngredientDefinition.Fluid fluid) {
            node.addProperty(fluid.tag()?"tag":"fluid",fluid.id().toString());
            node.addProperty("amount",fluid.amount());
        } else if(ingredient instanceof IngredientDefinition.Alternatives alternatives) {
            final var choices=new JsonArray();
            alternatives.choices().forEach(choice->choices.add(json(choice)));
            return choices;
        } else if(ingredient instanceof IngredientDefinition.Sized sized) {
            final var value=json(sized.ingredient());
            if(value.isJsonObject())value.getAsJsonObject().addProperty("count",sized.amount());
            return value;
        }
        return node;
    }

    private void chance(final Map<String,String> properties,final String evidence,final int slot) {
        try {
            final var value=com.google.gson.JsonParser.parseString(evidence);
            if(value.isJsonObject()&&value.getAsJsonObject().has("chance"))
                properties.put("Output "+slot+" chance",value.getAsJsonObject().get("chance").toString());
        } catch(com.google.gson.JsonParseException ignored) { /* Raw evidence stays available in its own tab. */ }
    }
}
