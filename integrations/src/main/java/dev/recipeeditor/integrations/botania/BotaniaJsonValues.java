package dev.recipeeditor.integrations.botania;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.util.*;

/** Verified Vanilla Ingredient/ItemStack values used by the three Botania 456 serializers. */
final class BotaniaJsonValues {
    record Ingredient(IngredientDefinition value,boolean partial) { }
    Ingredient ingredient(final JsonNode node){return ingredient(node,0);}
    private Ingredient ingredient(final JsonNode node,final int depth) {
        if(depth>16)throw new IllegalArgumentException("Ingredient nesting limit exceeded");
        if(node.isArray()) {
            if(node.isEmpty()||node.size()>256)throw new IllegalArgumentException("Ingredient alternatives must contain 1..256 entries");
            final var choices=new ArrayList<IngredientDefinition>();boolean partial=false;
            for(final var child:node){final var parsed=ingredient(child,depth+1);choices.add(parsed.value());partial|=parsed.partial();}
            return new Ingredient(new IngredientDefinition.Alternatives(choices),partial);
        }
        if(!node.isObject())throw new IllegalArgumentException("Ingredient must be an object or alternatives array");
        if(node.has("type")&&!node.path("type").asText().equals("minecraft:item"))return new Ingredient(new IngredientDefinition.Custom(node.path("type").asText(),node.toString()),true);
        if(node.has("item")==node.has("tag"))throw new IllegalArgumentException("Ingredient requires exactly one item or tag");
        final boolean item=node.has("item");final var id=id(node.path(item?"item":"tag"));
        final boolean partial=node.size()>(node.has("type")?2:1);
        return new Ingredient(item?new IngredientDefinition.Item(id):new IngredientDefinition.Tag(id),partial);
    }
    RecipeOutput.Fixed output(final JsonNode node) {
        if(!node.isObject())throw new IllegalArgumentException("Output must be an ItemStack object");
        final var id=id(node.path("item"));
        final int count=node.has("count")?integer(node.path("count"),1,"output count"):1;
        if(id.toString().equals("minecraft:air"))throw new IllegalArgumentException("Output must not be empty");
        return new RecipeOutput.Fixed(id,count,false,node.toString());
    }
    ResourceLocation id(final JsonNode node){if(!node.isTextual())throw new IllegalArgumentException("Resource ID must be text");return ResourceLocation.parse(node.asText());}
    int integer(final JsonNode node,final int minimum,final String label){if(!node.isIntegralNumber()||!node.canConvertToInt()||node.intValue()<minimum)throw new IllegalArgumentException("Invalid "+label);return node.intValue();}
}
