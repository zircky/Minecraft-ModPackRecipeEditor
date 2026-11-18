package dev.recipeeditor.exporters;

import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
final class VanillaExportSupport {
    private final Set<String> types=Set.of("crafting_shaped","crafting_shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","smithing_transform","smithing_trim");
    String check(final RecipeChange change,final ExportContext context) {
        if(!context.modpack().minecraftVersion().value().equals("1.20.1") || context.modpack().loader().type()!=ModLoaderType.FORGE) return "Only Minecraft 1.20.1 + Forge is verified for this export adapter";
        final var definitions=context.definitions(change); if(definitions.isEmpty())return "Original recipe type is unavailable";
        for(final var recipe:definitions) {
            final var type=recipe.type().location();
            if(type.isEmpty() || !type.get().namespace().equals("minecraft") || !types.contains(type.get().path())) return "No verified serializer/manager for "+recipe.type().rawId();
            if(recipe.status()!=RecipeParseStatus.PARSED) return "Only fully parsed Vanilla recipes are supported; custom properties/ingredients need an adapter";
            final JsonNode json;
            try{json=new ExportText().json(recipe.rawJson());}catch(RuntimeException invalid){return "Invalid JSON: "+invalid.getMessage();}
            if(json.has("group")&&!json.path("group").isTextual())return "group must be a string";
            if(json.has("show_notification")&&!json.path("show_notification").isBoolean())return "show_notification must be boolean";
            final Set<String> categories=type.get().path().startsWith("crafting_")?Set.of("building","redstone","equipment","misc"):Set.of("food","blocks","misc");
            if(json.has("category")&&(!json.path("category").isTextual()||!categories.contains(json.path("category").asText()))) return "Unknown category for this recipe serializer";
            for(final var output:recipe.outputs()) if(output instanceof RecipeOutput.Fixed fixed && fixed.id().toString().equals("minecraft:air")) return "Vanilla output cannot be minecraft:air";
        } return "";
    }
}
