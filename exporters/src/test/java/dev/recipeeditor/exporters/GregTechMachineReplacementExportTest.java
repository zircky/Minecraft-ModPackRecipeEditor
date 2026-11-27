package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.RecipeIndex;
import dev.recipeeditor.recipeengine.editor.RecipeEditorService;
import dev.recipeeditor.registry.TagIndex;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class GregTechMachineReplacementExportTest {
    @TempDir Path root;

    @Test void writesOldIdRemovalBeforeNewBuilderAndRefusesHalfOfMigration() throws Exception {
        final var fixtures=new ExportFixtures(root);
        final String json="""
                {"type":"gtceu:assembler","duration":20,"inputs":{"item":[{"content":{"item":"minecraft:dirt"}}]},
                 "outputs":{"item":[{"content":{"item":"minecraft:stone"}}]}}
                """;
        final var original=fixtures.original("test:assembler/original",json);
        final var target=fixtures.recipe("test:macerator/new",json.replace("gtceu:assembler","gtceu:macerator"));
        final var editor=new RecipeEditorService(new RecipeIndex(List.of(original),new TagIndex(List.of())),fixtures.validator);
        editor.migrate(original.id(),target);
        assertTrue(editor.effective().findById(original.id()).isEmpty());
        assertEquals(1,editor.effective().findById(target.id()).size());
        editor.undo();
        assertEquals(1,editor.effective().findById(original.id()).size());
        assertTrue(editor.snapshot().changes().isEmpty());
        editor.redo();
        final var context=fixtures.context(List.copyOf(editor.snapshot().changes().values()),List.of(original),
                ExportFormat.KUBEJS,Map.of("gtceu","7.1.4","kubejs","2001.6.4-build.120"),List.of(),OverwritePolicy.REFUSE);
        final var plan=fixtures.planner.plan(fixtures.request(context));
        assertTrue(plan.valid(),plan.diagnostics().toString());
        final String script=plan.files().values().stream().filter(value->value.contains("ServerEvents.recipes")).findFirst().orElseThrow();
        final int remove=script.indexOf("event.remove({id: \"test:assembler/original\"})");
        final int add=script.indexOf("event.recipes.gtceu.macerator(\"test:new\")");
        assertTrue(remove>=0 && add>remove,script);
        final var partial=fixtures.planner.plan(new ExportRequest(context,Set.of(original.id()),Map.of(),List.of()));
        assertFalse(partial.valid());
        assertTrue(partial.files().isEmpty());
        final var reopened=new RecipeEditorService(editor.originals(),fixtures.validator);
        reopened.restore(editor.snapshot());
        assertEquals(editor.snapshot(),reopened.snapshot());
    }
}
