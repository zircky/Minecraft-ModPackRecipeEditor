package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

/** Desktop DTO; chemical references are deliberately distinct from fluids. */
public record RecipeSlotContent(Kind kind, ResourceLocation id, long amount, List<RecipeSlotContent> alternatives, RecipeSlotState state,String nbt) {
    public enum Kind { EMPTY, ITEM, BLOCK, FLUID, CHEMICAL, TAG, ALTERNATIVES, UNKNOWN }
    public RecipeSlotContent(final Kind kind,final ResourceLocation id,final long amount,final List<RecipeSlotContent> alternatives,final RecipeSlotState state){this(kind,id,amount,alternatives,state,"");}
    public RecipeSlotContent { alternatives=List.copyOf(alternatives);Objects.requireNonNull(nbt);if(nbt.length()>8192)throw new IllegalArgumentException("Item NBT limit"); }
    public static RecipeSlotContent parse(final String json) {
        if(json.isBlank())return new RecipeSlotContent(Kind.EMPTY,null,0,List.of(),RecipeSlotState.EMPTY);
        try { return read(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json),0); }
        catch(Exception error){return new RecipeSlotContent(Kind.UNKNOWN,null,0,List.of(),RecipeSlotState.INVALID);}
    }
    private static RecipeSlotContent read(final com.fasterxml.jackson.databind.JsonNode node,final int depth) {
        if(depth>16)throw new IllegalArgumentException("Ingredient depth limit");
        if(node.isArray()) {
            final var choices=new ArrayList<RecipeSlotContent>();for(final var child:node)choices.add(read(child,depth+1));
            return new RecipeSlotContent(Kind.ALTERNATIVES,null,1,choices,choices.isEmpty()?RecipeSlotState.INVALID:RecipeSlotState.FILLED);
        }
        final long amount=node.path("count").asLong(node.path("amount").asLong(1));
        if(amount<1)throw new IllegalArgumentException("Positive amount required");
        if(node.isTextual())return new RecipeSlotContent(Kind.ITEM,ResourceLocation.parse(node.asText()),amount,List.of(),RecipeSlotState.FILLED);
        for(final var kind:List.of(Kind.ITEM,Kind.BLOCK,Kind.FLUID,Kind.CHEMICAL,Kind.TAG)) {
            final String key=kind.name().toLowerCase(Locale.ROOT);
            if(node.path(key).isTextual())return new RecipeSlotContent(kind,ResourceLocation.parse(node.path(key).asText()),amount,List.of(),RecipeSlotState.FILLED,node.has("nbt")?(node.get("nbt").isTextual()?node.get("nbt").textValue():node.get("nbt").toString()):"");
        }
        return new RecipeSlotContent(Kind.UNKNOWN,null,amount,List.of(),RecipeSlotState.UNKNOWN);
    }
}
