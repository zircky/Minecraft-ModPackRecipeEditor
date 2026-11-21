package dev.recipeeditor.registry;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class VanillaAssetLocatorTest {
    @TempDir Path root;
    @Test void clientModelsAndLanguageObjectsUseBoundedVerifiedLocationsAndKeepSourceEvidence() throws Exception {
        final var versions=Files.createDirectories(root.resolve("versions/1.20.1"));final var jar=versions.resolve("1.20.1.jar");
        try(final var zip=new ZipOutputStream(Files.newOutputStream(jar))) {
            for(final var entry:Map.of("assets/minecraft/models/item/diamond.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/diamond\"}}",
                    "assets/minecraft/lang/en_us.json","{\"item.minecraft.diamond\":\"Diamond\"}","data/minecraft/recipes/diamond.json","{}").entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
        }
        final byte[] language="{\"item.minecraft.diamond\":\"Test translated name\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final String hash=hash(language);final var object=root.resolve("assets/objects/"+hash.substring(0,2)+"/"+hash);Files.createDirectories(object.getParent());Files.write(object,language);
        final String index="{\"objects\":{\"minecraft/lang/ru_ru.json\":{\"hash\":\""+hash+"\",\"size\":"+language.length+"}}}";
        final var indexFile=root.resolve("assets/indexes/5.json");Files.createDirectories(indexFile.getParent());Files.writeString(indexFile,index);
        Files.writeString(versions.resolve("1.20.1.json"),"{\"id\":\"1.20.1\",\"downloads\":{\"client\":"+descriptor(Files.readAllBytes(jar))+"},\"assetIndex\":{\"id\":\"5\",\"size\":"+Files.size(indexFile)+",\"sha1\":\""+hash(Files.readAllBytes(indexFile))+"\"}}");
        final var pack=ResourceFixtures.pack(root,ResourceFixtures.jar(root,Map.of()));
        final var scan=new ResourceScanner(new DiscoveryLimits(),ResourceLayout.minecraft1201()).scan(pack,p->{});
        assertTrue(scan.documents().stream().anyMatch(doc->doc.source().type()==SourceType.VANILLA_ASSET&&doc.source().internalPath().endsWith("models/item/diamond.json")));
        assertFalse(scan.documents().stream().anyMatch(doc->doc.source().internalPath().startsWith("data/minecraft/recipes")),"Asset loading must not import a new recipe origin");
        final var names=new LocalizationService(scan.documents()).translations("item.minecraft.diamond");
        assertEquals("Diamond",names.get("en_us"));assertEquals("Test translated name",names.get("ru_ru"));
        assertEquals(Set.of(jar.toRealPath()),new VanillaAssetLocator(List.of()).approvedArchives(root));
        Files.write(jar,new byte[]{1,2,3});
        assertTrue(new VanillaAssetLocator(List.of()).find(pack).archives().isEmpty(),"A modified client must fail integrity validation");
    }
    @Test void absentOrUnsafeMetadataDoesNotInventAssetsOrEscapeTheInstance() throws Exception {
        final var pack=ResourceFixtures.pack(root,ResourceFixtures.jar(root,Map.of()));
        assertTrue(new VanillaAssetLocator(List.of()).find(pack).archives().isEmpty());
        final var source=new ResourceSource(SourceType.VANILLA_ASSET,List.of(),root.resolveSibling("outside.jar"),"assets/minecraft/textures/item/diamond.png","forged");
        try(final var textures=new TextureResourceService()){assertEquals(0,textures.load(source,root).length);}
    }
    private String descriptor(final byte[] data)throws Exception{return "{\"size\":"+data.length+",\"sha1\":\""+hash(data)+"\"}";}
    private String hash(final byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));}
}
