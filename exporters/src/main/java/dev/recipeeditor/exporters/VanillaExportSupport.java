package dev.recipeeditor.exporters;

import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
final class VanillaExportSupport {
    private final RecipeValidator validator=new RecipeValidator(new dev.recipeeditor.integrations.Vanilla1201Parsers().parsers(),new dev.recipeeditor.registry.RegistryIndex(List.of()));
    private final Set<String> types=Set.of("crafting_shaped","crafting_shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","smithing_transform","smithing_trim");
    String check(final RecipeChange change,final ExportContext context) {
        return check(change,context,false);
    }
    String checkKubeJs(final RecipeChange change,final ExportContext context){return check(change,context,true);}
    private String check(final RecipeChange change,final ExportContext context,final boolean opaqueRootFields) {
        if(!context.modpack().minecraftVersion().value().equals("1.20.1") || context.modpack().loader().type()!=ModLoaderType.FORGE) return "Only Minecraft 1.20.1 + Forge is verified for this export adapter";
        final var definitions=context.definitions(change); if(definitions.isEmpty())return "Original recipe type is unavailable";
        for(final var recipe:definitions) {
            final var type=recipe.type().location();
            if(type.isEmpty() || !type.get().namespace().equals("minecraft") || !types.contains(type.get().path())) return "No verified serializer/manager for "+recipe.type().rawId();
            if(recipe.source().type()==dev.recipeeditor.registry.SourceType.RUNTIME && !new RuntimeRecipeBase().matches(recipe.source()))
                return "Normalized runtime state requires a verified serializer projection";
            final JsonNode json;
            try{json=new ExportText().json(recipe.rawJson());}catch(RuntimeException invalid){return "Invalid JSON: "+invalid.getMessage();}
            if(!json.isObject() || !json.path("type").isTextual() || !json.path("type").asText().equals(recipe.type().rawId()))
                return "Recipe type metadata does not match serializer type in JSON";
            if(Set.of("smelting","blasting","smoking","campfire_cooking").contains(type.get().path()) && !json.path("result").isTextual())
                return "Minecraft 1.20.1 cooking result must be an item ID string";
            if(json.path("result").asText().equals("minecraft:air") || json.path("result").path("item").asText().equals("minecraft:air"))
                return "Vanilla output cannot be minecraft:air";
            if(opaqueRootFields){final String compatibility=checkRootExtensions(recipe,json,type.get().path());if(!compatibility.isEmpty())return compatibility;}
            else {
                final var parsed=validator.validate(recipe.id(),recipe.rawJson(),recipe.source());
                if(!parsed.valid() || parsed.recipe().status()!=RecipeParseStatus.PARSED)
                    return "Only fully parsed Vanilla recipes are supported; invalid properties/custom ingredients need an adapter";
            }
            if(json.has("group")&&!json.path("group").isTextual())return "group must be a string";
            if(json.has("show_notification")&&!json.path("show_notification").isBoolean())return "show_notification must be boolean";
            final Set<String> categories=type.get().path().startsWith("crafting_")?Set.of("building","redstone","equipment","misc"):Set.of("food","blocks","misc");
            if(json.has("category")&&(!json.path("category").isTextual()||!categories.contains(json.path("category").asText()))) return "Unknown category for this recipe serializer";
            for(final var output:recipe.outputs()) if(output instanceof RecipeOutput.Fixed fixed && fixed.id().toString().equals("minecraft:air")) return "Vanilla output cannot be minecraft:air";
        } return "";
    }
    private String checkRootExtensions(final RecipeDefinition recipe,final JsonNode json,final String type){
        if(!json.isObject()||recipe.status()==RecipeParseStatus.INVALID||recipe.status()==RecipeParseStatus.UNSUPPORTED)return "Invalid or unsupported Vanilla serializer JSON";
        final var allowed=new HashSet<>(Set.of("type","group","category","show_notification"));
        allowed.addAll(switch(type){
            case "crafting_shaped" -> Set.of("pattern","key","result");
            case "crafting_shapeless" -> Set.of("ingredients","result");
            case "smithing_transform","smithing_trim" -> Set.of("template","base","addition","result");
            case "stonecutting" -> Set.of("ingredient","result","count");
            default -> Set.of("ingredient","result","experience","cookingtime");
        });
        final var projected=json.deepCopy();final var fields=json.fieldNames();
        while(fields.hasNext()){
            final String field=fields.next();if(allowed.contains(field))continue;
            if(field.toLowerCase(Locale.ROOT).contains("condition")||field.contains(":"))return "Extension field requires an integration adapter: "+field;
            ((com.fasterxml.jackson.databind.node.ObjectNode)projected).remove(field);
        }
        // Vanilla serializers ignore unconsumed ordinary root keys. Nested ingredient/result extensions do not get this exemption.
        final var parsed=validator.validate(recipe.id(),projected.toString(),recipe.source());
        return parsed.valid()&&parsed.recipe().status()==RecipeParseStatus.PARSED?"":"Custom ingredient/result semantics or invalid required properties need an adapter";
    }
}
