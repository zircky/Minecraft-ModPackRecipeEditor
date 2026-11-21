package dev.recipeeditor.app;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.exporters.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("external") @EnabledIfSystemProperty(named="realModpack",matches=".+")
final class RecipeCapabilityResolverTest {
    @TempDir Path root;
    @Test void allExistingExportFormatsRejectBotaniaRatherThanInventingKubeJsCalls()throws Exception {
        try(final var artifact=new BotaniaTestArtifact();final var importer=new dev.recipeeditor.modpackparser.DefaultModpackImportService()) {
            final var original=artifact.recipe("data/botania/recipes/brew/allure.json");final var index=new RecipeIndex(List.of(original),new TagIndex(List.of()));final var editor=new RecipeEditorService(index,artifact.validator);
            final var change=new RecipeChange(original.id(),RecipeChangeType.UPDATE,List.of(OriginalRecipeReference.from(original)),List.of(original),java.time.Instant.EPOCH,Map.of());
            final var pack=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(30,TimeUnit.SECONDS).modpack();
            final var resolver=new ExportCapabilityResolver(List.of(new DatapackRecipeExporter(),new KubeJsRecipeExporter(),new CraftTweakerRecipeExporter()));
            for(final var format:ExportFormat.values()) {
                final var profile=new ExportProfile("phase16test","Botania negative capability",root.toAbsolutePath(),format,OverwritePolicy.OWNED_UNCHANGED,true,"1.20.1","FORGE");
                final var context=new ExportContext(pack,index,new RecipeChangeSet(Map.of(original.id(),change)),root.resolve("project.json"),profile,List.of(),Set.of(),false);
                assertFalse(resolver.resolve(change,context,format).supported(),format.toString());
            }
        }
    }
}
