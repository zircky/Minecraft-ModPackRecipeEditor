package dev.recipeeditor.integrations;
import dev.recipeeditor.integrations.api.ModdedRecipeAdapterRegistry;
import dev.recipeeditor.integrations.botania.BotaniaRecipeAdapter;
import java.util.List;
/** Composition point for verified static serializers; no runtime JSON is synthesized. */
public final class ProjectRecipeAdapters {
    public ModdedRecipeAdapterRegistry registry(){return new ModdedRecipeAdapterRegistry(List.of(new BotaniaRecipeAdapter()));}
}
