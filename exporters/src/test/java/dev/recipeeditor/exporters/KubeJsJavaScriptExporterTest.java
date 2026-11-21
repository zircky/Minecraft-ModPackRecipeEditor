package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class KubeJsJavaScriptExporterTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings={"crafting_shaped","crafting_shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","smithing_transform","smithing_trim"})
    void createsAllNineVanillaSerializersWithExactIdsAndLosslessJson(final String type)throws Exception{
        final var f=new ExportFixtures(root);final var helper=new KubeJsTestSupport();final String raw=helper.recipe(type);final var change=f.create("minecraft:nested/"+type,raw);
        final var c=helper.context(f,List.of(change),List.of(),KubeJsExportMode.JAVASCRIPT);final var plan=f.planner.plan(f.request(c));assertTrue(plan.valid(),plan.diagnostics().toString());
        final String script=plan.files().values().iterator().next();assertTrue(script.contains("ServerEvents.recipes"));assertTrue(script.contains(".id(\"minecraft:nested/"+type+"\")"));assertTrue(script.contains(new ExportText().quote(raw)));assertFalse(script.contains("event.remove"));
        assertEquals(List.of("kubejs/server_scripts/recipe_editor_testprofile_generated.js"),plan.recipes().getFirst().targetFiles());
    }
    @Test void updateRemovesOnlyExactIdThenAddsOnceAndDeleteNeverAdds()throws Exception{
        final var f=new ExportFixtures(root);final var original=f.original("minecraft:tnt",f.raw);final var helper=new KubeJsTestSupport();
        for(final var operation:List.of(RecipeChangeType.UPDATE,RecipeChangeType.DELETE)){
            final var change=f.change(original,operation,operation==RecipeChangeType.DELETE?"":f.raw);final var c=helper.context(f,List.of(change),List.of(original),KubeJsExportMode.JAVASCRIPT);
            final String script=new KubeJsRecipeExporter().generate(List.of(change),c).values().iterator().next();
            assertEquals(1,script.split("event.remove",-1).length-1);assertTrue(script.contains("event.remove({id: \"minecraft:tnt\"})"));assertFalse(script.contains("{mod:"));assertFalse(script.contains("{output:"));
            assertEquals(operation==RecipeChangeType.DELETE?0:1,script.split("event.custom",-1).length-1);
        }
    }
}
