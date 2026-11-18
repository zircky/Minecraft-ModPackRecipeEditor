package dev.recipeeditor.exporters;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.io.TempDir;
import dev.recipeeditor.recipeengine.editor.RecipeChangeType;
import static org.junit.jupiter.api.Assertions.*;

final class DatapackRecipeExporterTest {
    @TempDir Path root;
    static java.util.stream.Stream<Arguments> types() {return java.util.stream.Stream.of(
        Arguments.of("crafting_shaped","\"pattern\":[\"A\"],\"key\":{\"A\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"item\":\"minecraft:iron_block\"}"),
        Arguments.of("crafting_shapeless","\"ingredients\":[{\"tag\":\"forge:ingots/iron\"}],\"result\":{\"item\":\"minecraft:iron_block\",\"count\":2}"),
        Arguments.of("smelting","\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"minecraft:iron_ingot\",\"experience\":0.7,\"cookingtime\":200"),
        Arguments.of("blasting","\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"minecraft:iron_ingot\""),
        Arguments.of("smoking","\"ingredient\":{\"item\":\"minecraft:beef\"},\"result\":\"minecraft:cooked_beef\""),
        Arguments.of("campfire_cooking","\"ingredient\":{\"item\":\"minecraft:beef\"},\"result\":\"minecraft:cooked_beef\""),
        Arguments.of("stonecutting","\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:stone_slab\",\"count\":2"),
        Arguments.of("smithing_transform","\"template\":{\"item\":\"minecraft:netherite_upgrade_smithing_template\"},\"base\":{\"item\":\"minecraft:diamond_sword\"},\"addition\":{\"item\":\"minecraft:netherite_ingot\"},\"result\":{\"item\":\"minecraft:netherite_sword\"}"),
        Arguments.of("smithing_trim","\"template\":{\"tag\":\"minecraft:trim_templates\"},\"base\":{\"tag\":\"minecraft:trimmable_armor\"},\"addition\":{\"tag\":\"minecraft:trim_materials\"}"));}
    @ParameterizedTest @MethodSource("types") void preservesVersionSpecificJson(final String type,final String fields)throws Exception {
        final var f=new ExportFixtures(root);final String json="{\"type\":\"minecraft:"+type+"\","+fields+"}";
        final var plan=f.plan(List.of(f.create("test:folder/recipe",json)),ExportFormat.DATAPACK);
        assertTrue(plan.valid(),plan.diagnostics().toString());assertEquals(json+"\n",plan.files().get("datapacks/recipe_editor_testprofile/data/test/recipes/folder/recipe.json"));
        assertEquals(15,new ExportText().json(plan.files().get("datapacks/recipe_editor_testprofile/pack.mcmeta")).path("pack").path("pack_format").asInt());
        assertEquals(0,Files.list(f.target).count(),"Dry Run must not write files");
    }
    @Test void updatePreservesIdAndDeleteIsUnsupported()throws Exception {final var f=new ExportFixtures(root);final var base=f.original("test:base",f.raw);final var ctx=f.context(List.of(),List.of(base),ExportFormat.DATAPACK,Map.of(),List.of(),OverwritePolicy.OWNED_UNCHANGED);final var exporter=new DatapackRecipeExporter();assertTrue(exporter.checkCapability(f.change(base,RecipeChangeType.UPDATE,f.raw),ctx).supported());assertFalse(exporter.checkCapability(f.change(base,RecipeChangeType.DELETE,""),ctx).supported());}
    @Test void refusesUnknownPropertiesAndWrongCategory()throws Exception {final var f=new ExportFixtures(root);assertFalse(f.plan(List.of(f.create("test:new",f.raw.replace("\"ingredients\"","\"unknown\":1,\"ingredients\""))),ExportFormat.DATAPACK).valid());assertFalse(f.plan(List.of(f.create("test:new",f.raw.replace("\"ingredients\"","\"category\":\"food\",\"ingredients\""))),ExportFormat.DATAPACK).valid());}
}
