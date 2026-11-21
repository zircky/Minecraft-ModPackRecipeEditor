package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class KubeJsDataExporterTest {
    @TempDir Path root;
    @Test void createAndUpdateKeepOriginalNestedNameNamespaceTagsCountAndUnknownRootFields()throws Exception{
        final var f=new ExportFixtures(root);final var helper=new KubeJsTestSupport();final String raw=f.raw.substring(0,f.raw.length()-1)+",\"unknown_metadata\":{\"nested\":[1,2]}}";
        final var base=f.original("minecraft:tools/iron_hammer",f.raw);
        for(final var operation:List.of(RecipeChangeType.CREATE,RecipeChangeType.UPDATE)){
            final var change=operation==RecipeChangeType.CREATE?f.create("minecraft:tools/iron_hammer",raw):f.change(base,operation,raw);
            final var c=helper.context(f,List.of(change),operation==RecipeChangeType.CREATE?List.of():List.of(base),KubeJsExportMode.DATA_JSON);
            final var files=new KubeJsRecipeExporter().generate(List.of(change),c);assertEquals(Set.of("kubejs/data/minecraft/recipes/tools/iron_hammer.json"),files.keySet());
            assertEquals(raw+"\n",files.values().iterator().next());assertFalse(files.keySet().stream().anyMatch(path->path.endsWith(".js")));
        }
    }
    @Test void directDeleteIsUnsupportedWithoutEmptyJsonOrSilentFallback()throws Exception{
        final var f=new ExportFixtures(root);final var base=f.original("minecraft:tnt",f.raw);final var change=f.change(base,RecipeChangeType.DELETE,"");final var c=new KubeJsTestSupport().context(f,List.of(change),List.of(base),KubeJsExportMode.DATA_JSON);
        final var plan=f.planner.plan(f.request(c));assertFalse(plan.valid());assertTrue(plan.files().isEmpty());assertFalse(plan.recipes().getFirst().capability().supported());assertTrue(plan.recipes().getFirst().capability().reason().contains("JAVASCRIPT"));assertThrows(IllegalArgumentException.class,()->new KubeJsDataExporter().generate(List.of(change),c));
    }
}
