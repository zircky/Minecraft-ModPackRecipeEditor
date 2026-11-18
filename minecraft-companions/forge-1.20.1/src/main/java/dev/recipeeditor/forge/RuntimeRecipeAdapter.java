package dev.recipeeditor.forge;
import dev.recipeeditor.companionprotocol.RuntimeData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.RegistryAccess;
public interface RuntimeRecipeAdapter {
    boolean supports(Recipe<?> recipe);
    RuntimeData.Recipe convert(Recipe<?> recipe,RegistryAccess registries);
}
