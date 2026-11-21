package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.RecipeDefinition;
import dev.recipeeditor.recipeengine.editor.RecipeChange;
import java.util.*;

final class KubeJsTestSupport {
    ExportContext context(final ExportFixtures f,final List<RecipeChange> changes,final List<RecipeDefinition> originals,final KubeJsExportMode mode){
        final var c=f.context(changes,originals,ExportFormat.KUBEJS,Map.of("kubejs","2001.6.5-build.26"),List.of(),OverwritePolicy.OWNED_UNCHANGED);
        return mode(c,mode,List.of());
    }
    ExportContext mode(final ExportContext c,final KubeJsExportMode mode,final List<ExportManifest> manifests){return new ExportContext(c.modpack(),c.originals(),c.savedChanges(),c.projectFile(),c.profile().withKubeJsMode(mode),manifests,c.blockedIds(),c.allowSourceDirectory());}
    String recipe(final String type){
        final String ingredients="\"template\":{\"item\":\"minecraft:coast_armor_trim_smithing_template\"},\"base\":{\"item\":\"minecraft:iron_chestplate\"},\"addition\":{\"item\":\"minecraft:quartz\"}";
        final String body=switch(type){
            case "crafting_shaped" -> "\"pattern\":[\"A\"],\"key\":{\"A\":{\"tag\":\"forge:ingots/iron\"}},\"result\":{\"item\":\"minecraft:iron_block\",\"count\":2}";
            case "crafting_shapeless" -> "\"ingredients\":[{\"tag\":\"forge:ingots/iron\"}],\"result\":{\"item\":\"minecraft:iron_block\",\"count\":2}";
            case "smithing_trim" -> ingredients;
            case "smithing_transform" -> ingredients+",\"result\":{\"item\":\"minecraft:netherite_chestplate\"}";
            case "stonecutting" -> "\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:stone_slab\",\"count\":2";
            default -> "\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"minecraft:iron_ingot\",\"experience\":0.35,\"cookingtime\":100";
        };
        return "{\"type\":\"minecraft:"+type+"\","+body+"}";
    }
}
