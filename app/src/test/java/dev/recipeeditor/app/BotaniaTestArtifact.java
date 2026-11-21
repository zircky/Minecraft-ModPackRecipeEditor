package dev.recipeeditor.app;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.RecipeValidator;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads genuine samples at test time; third-party code/assets are not redistributed as fixtures. */
final class BotaniaTestArtifact implements AutoCloseable {
    final Path path=Path.of(System.getProperty("realModpack")).resolve("minecraft/mods/Botania-1.20.1-456-FORGE.jar");
    final ZipFile zip;
    final LocalizationService localization;
    final RecipeValidator validator=new RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),new RegistryIndex(List.of()));
    BotaniaTestArtifact()throws Exception {
        final var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        if(!hash.equals("3cf540357c9cb5e460266b17a02617656df6cc7090a565fca2a462b1c3aba4ae"))throw new IllegalArgumentException("Botania artifact differs from the verified 456 fixture");
        zip=new ZipFile(path.toFile());final var documents=new ArrayList<ResourceDocument>();
        for(final String language:List.of("en_us","ru_ru")){final String resource="assets/botania/lang/"+language+".json";documents.add(new ResourceDocument(new ResourceSource(SourceType.MOD_JAR,List.of("botania"),path,resource,"genuine test artifact"),read(resource)));}
        localization=new LocalizationService(documents);
    }
    String read(final String resource)throws Exception {try(final var stream=zip.getInputStream(zip.getEntry(resource))){return new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    RecipeDefinition recipe(final String resource)throws Exception {
        final var id=ResourceLocation.parse(resource.substring(5,resource.indexOf("/recipes/"))+":"+resource.substring(resource.indexOf("/recipes/")+9,resource.length()-5));
        final var source=new RecipeSource(SourceType.MOD_JAR,List.of("botania"),path,resource,"genuine installed Botania 456 JSON");
        return new RecipeSemanticLocalization().localize(validator.validate(id,read(resource),source).recipe(),localization);
    }
    List<RecipeDefinition> recipes()throws Exception {
        final var result=new ArrayList<RecipeDefinition>();
        for(final var entry:java.util.Collections.list(zip.entries()))if(entry.getName().startsWith("data/botania/recipes/")&&entry.getName().endsWith(".json")) {
            final var raw=new com.fasterxml.jackson.databind.ObjectMapper().readTree(read(entry.getName()));
            if(Set.of("botania:brew","botania:petal_apothecary","botania:mana_infusion").contains(raw.path("type").asText()))result.add(recipe(entry.getName()));
        }
        return List.copyOf(result);
    }
    @Override public void close()throws java.io.IOException {zip.close();}
}
