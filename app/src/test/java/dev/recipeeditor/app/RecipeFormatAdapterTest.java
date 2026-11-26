package dev.recipeeditor.app;

import dev.recipeeditor.core.importing.ModLoaderType;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.RecipeParseStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class RecipeFormatAdapterTest {
    private static final String PAYLOAD = """
            {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:dirt"}],
             "result":{"item":"minecraft:stone"},"futureField":{"nested":[1,true]}}
            """;

    @TempDir Path instance;

    @ParameterizedTest
    @CsvSource({"1.20.1,net.minecraftforge,47.4.28,FORGE,data/test/recipes/nested/sample.json,PARTIALLY_PARSED",
            "1.21.1,net.neoforged,21.1.172,NEOFORGE,data/test/recipe/nested/sample.json,UNSUPPORTED",
            "1.16.5,net.minecraftforge,36.2.39,FORGE,data/test/recipes/nested/sample.json,UNSUPPORTED",
            "1.12.2,net.minecraftforge,14.23.5.2860,FORGE,assets/test/recipes/nested/sample.json,UNSUPPORTED",
            "1.7.10,net.minecraftforge,10.13.4.1614,FORGE,data/test/recipes/nested/sample.json,NONE"})
    void importsFromFilesWithoutCompanion(final String version, final String loaderUid, final String loaderVersion,
            final ModLoaderType expectedLoader, final String recipePath, final String expectedStatus) throws Exception {
        Files.writeString(instance.resolve("mmc-pack.json"), """
                {"components":[{"uid":"net.minecraft","version":"%s"},{"uid":"%s","version":"%s"}]}
                """.formatted(version, loaderUid, loaderVersion));
        final Path mods = Files.createDirectory(instance.resolve("mods"));
        final Path jar = mods.resolve("fixture.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry(recipePath));
            zip.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("data/test/wrong_recipe_dir/ignore.json"));
            zip.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        try (var importer = new DefaultModpackImportService(); var analysis = new DefaultStaticAnalysisService()) {
            final var pack = importer.start(instance, progress -> {}).result().get(15, TimeUnit.SECONDS).modpack();
            assertEquals(version, pack.minecraftVersion().value());
            assertEquals(expectedLoader, pack.loader().type());
            assertEquals(loaderVersion, pack.loader().version());
            final var result = analysis.start(pack, progress -> {}).result().get(20, TimeUnit.SECONDS);
            final var recipes = result.recipes().recipes();
            if (expectedStatus.equals("NONE")) {
                assertTrue(recipes.isEmpty(), "Legacy code registrations must not be treated as datapack recipes");
                return;
            }
            assertEquals(1, recipes.size());
            final var recipe = recipes.getFirst();
            assertEquals("test:nested/sample", recipe.id().toString());
            assertEquals(recipePath, recipe.source().internalPath());
            assertEquals(RecipeParseStatus.valueOf(expectedStatus), recipe.status());
            assertEquals(PAYLOAD, recipe.rawJson());
            if (recipe.status() == RecipeParseStatus.PARTIALLY_PARSED) assertFalse(recipe.inputs().isEmpty());
        }
    }
}
