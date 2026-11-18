package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import com.google.gson.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.core.RegistryAccess;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
/** Exact Vanilla implementations only: subclasses may change semantics and require another adapter. */
public final class VanillaRecipeAdapter implements RuntimeRecipeAdapter {
    private final Set<Class<?>> known=Set.of(ShapedRecipe.class,ShapelessRecipe.class,SmeltingRecipe.class,BlastingRecipe.class,SmokingRecipe.class,CampfireCookingRecipe.class,StonecutterRecipe.class,SmithingTransformRecipe.class,SmithingTrimRecipe.class);
    @Override public boolean supports(final Recipe<?> recipe){return known.contains(recipe.getClass());}
    @Override public RuntimeData.Recipe convert(final Recipe<?> recipe,final RegistryAccess registries) {
        final var properties=new JsonObject();properties.addProperty("group",recipe.getGroup());properties.addProperty("sourceJsonAvailable",false);
        final var inputs=new JsonArray();recipe.getIngredients().forEach(ingredient->inputs.add(ingredient.toJson()));properties.add("ingredients",inputs);
        if(recipe instanceof ShapedRecipe shaped){properties.addProperty("width",shaped.getWidth());properties.addProperty("height",shaped.getHeight());properties.addProperty("category",shaped.category().getSerializedName());properties.addProperty("showNotification",shaped.showNotification());}
        if(recipe instanceof ShapelessRecipe shapeless)properties.addProperty("category",shapeless.category().getSerializedName());
        if(recipe instanceof AbstractCookingRecipe cooking){properties.addProperty("experience",cooking.getExperience());properties.addProperty("cookingTime",cooking.getCookingTime());properties.addProperty("category",cooking.category().getSerializedName());}
        if(recipe instanceof SmithingTrimRecipe){properties.addProperty("dynamicResult",true);return new GenericRecipeAdapter().metadata(recipe,"vanilla-1.20.1",RuntimeData.Support.PARTIALLY_SUPPORTED,properties,List.of("Smithing trim output depends on input stacks. Private template/base/addition are not reconstructed."));}
        final var result=recipe.getResultItem(registries);final var output=new JsonObject();final var itemId=ForgeRegistries.ITEMS.getKey(result.getItem());if(itemId!=null)output.addProperty("item",itemId.toString());else output.addProperty("registryIdAvailable",false);output.addProperty("empty",result.isEmpty());output.addProperty("count",result.getCount());if(result.hasTag())output.addProperty("nbtSnbt",result.getTag().toString());properties.add("result",output);
        final boolean partial=recipe instanceof SmithingTransformRecipe||itemId==null||result.isEmpty()||!simple(inputs);return new GenericRecipeAdapter().metadata(recipe,"vanilla-1.20.1",partial?RuntimeData.Support.PARTIALLY_SUPPORTED:RuntimeData.Support.SUPPORTED,properties,List.of(partial?"Smithing roles, custom ingredients or unavailable result metadata limit this normalized view.":"Normalized loaded state; this is not the original source JSON."));
    }
    private boolean simple(final JsonElement value) {
        if(value.isJsonArray()){for(final var item:value.getAsJsonArray())if(!simple(item))return false;return true;}
        if(!value.isJsonObject())return false;final var object=value.getAsJsonObject();return object.size()==1&&(object.has("item")||object.has("tag"));
    }
}
