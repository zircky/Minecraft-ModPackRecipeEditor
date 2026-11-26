package dev.recipeeditor.exporters;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class RuntimeRecipeExportTest {
    @TempDir Path root;

    @Test void exportsValidatedOverlayAndExactIdRemovalFromMatchingRuntimeEvidence() throws Exception {
        final var fixtures=new ExportFixtures(root);
        final var id=ResourceLocation.parse("test:loaded_recipe");
        final var source=new RuntimeRecipeBase().source(fixtures.source,"vanilla-1.20.1","minecraft:crafting","minecraft:crafting_shapeless");
        final var original=fixtures.validator.validate(id,fixtures.raw,source).recipe();
        final var update=fixtures.change(original,RecipeChangeType.UPDATE,fixtures.raw.replace("iron_block","gold_block"));
        final var delete=fixtures.change(original,RecipeChangeType.DELETE,"");
        final var mods=Map.of("kubejs","2001.6.4-build.120");
        final var datapack=fixtures.planner.plan(fixtures.request(fixtures.context(List.of(update),List.of(original),
                ExportFormat.DATAPACK,mods,List.of(),OverwritePolicy.REFUSE)));
        assertTrue(datapack.valid(),datapack.diagnostics().toString());
        assertTrue(datapack.files().values().stream().anyMatch(text->text.contains("minecraft:gold_block")));
        final var kubejs=fixtures.planner.plan(fixtures.request(fixtures.context(List.of(delete),List.of(original),
                ExportFormat.KUBEJS,mods,List.of(),OverwritePolicy.REFUSE)));
        assertTrue(kubejs.valid(),kubejs.diagnostics().toString());
        assertTrue(kubejs.files().values().stream().anyMatch(text->text.contains("event.remove({id: \"test:loaded_recipe\"})")));
        final var missing=fixtures.planner.plan(fixtures.request(fixtures.context(List.of(update),List.of(),
                ExportFormat.DATAPACK,mods,List.of(),OverwritePolicy.REFUSE)));
        assertFalse(missing.valid());
        assertTrue(missing.files().isEmpty());
    }
}
