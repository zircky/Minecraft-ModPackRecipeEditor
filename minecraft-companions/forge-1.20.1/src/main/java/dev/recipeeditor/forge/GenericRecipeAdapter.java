package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import com.google.gson.JsonObject;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.RegistryAccess;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
/** Unknown recipes expose metadata only; arbitrary ingredient/result methods are never invoked. */
public final class GenericRecipeAdapter implements RuntimeRecipeAdapter {
    @Override public boolean supports(final Recipe<?> recipe){return true;}
    @Override public RuntimeData.Recipe convert(final Recipe<?> recipe,final RegistryAccess registries) {
        return metadata(recipe,"generic",RuntimeData.Support.UNSUPPORTED,new JsonObject(),List.of("No semantic adapter. Source JSON and original datapack provenance are unknown."));
    }
    public RuntimeData.Recipe metadata(final Recipe<?> recipe,final String adapter,final RuntimeData.Support status,final JsonObject properties,final List<String> diagnostics) {
        return new RuntimeData.Recipe(recipe.getId().toString(),Objects.toString(ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType()),"UNKNOWN"),Objects.toString(ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer()),"UNKNOWN"),recipe.getClass().getName(),adapter,status,properties,"Runtime server collection; original pack/source UNKNOWN",diagnostics);
    }
}
