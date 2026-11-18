package dev.recipeeditor.registry;

import dev.recipeeditor.core.importing.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class ResourceFixtures {
    private ResourceFixtures() { }
    static Path jar(final Path root,final Map<String,String> entries) throws IOException {
        final var path=root.resolve("mods/test.jar"); Files.createDirectories(path.getParent());
        try(final var zip=new ZipOutputStream(Files.newOutputStream(path))) {
            for(final var entry:entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        } return path;
    }
    static ModpackDefinition pack(final Path root,final Path jar) {
        return new ModpackDefinition(root,root,"test",new MinecraftVersion("1.20.1",List.of()),new ModLoaderDefinition(ModLoaderType.FORGE,"47",List.of()),
                List.of(new ModScanResult(jar,List.of(),ScanStatus.OK,List.of())));
    }
    static ResourceDocument document(final String path,final String json) {
        return new ResourceDocument(new ResourceSource(SourceType.MOD_JAR,List.of("test"),Path.of("test.jar"),path,"test"),json);
    }
}
