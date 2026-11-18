package dev.recipeeditor.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Small, deterministic data-only modpack. No executable mod or Minecraft installation. */
public final class SyntheticModpack {
    private final Path directory;
    private final Path jar;
    private final Map<String, String> entries = new TreeMap<>();

    public SyntheticModpack(final Path directory) throws IOException {
        this.directory = Files.createDirectories(directory).toRealPath();
        jar = Files.createDirectories(this.directory.resolve("mods")).resolve("fixture.jar");
        Files.writeString(this.directory.resolve("manifest.json"), """
                {"minecraft":{"version":"1.20.1","modLoaders":[{"id":"forge-47.4.10"}]}}
                """);
        entries.put("META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"
                [[mods]]
                modId="fixture"
                version="1.0.0"
                displayName="Synthetic data fixture, not a runnable mod"
                [[mods]]
                modId="kubejs"
                version="2001.6.4-build.120"
                displayName="Synthetic dependency metadata"
                [[mods]]
                modId="crafttweaker"
                version="14.0.57"
                displayName="Synthetic dependency metadata"
                """);
        entries.put("assets/fixture/lang/en_us.json", """
                {"item.fixture.ore":"Fixture Ore","item.fixture.ingot":"Fixture Ingot"}
                """);
        for (final String item : List.of("ore", "ingot", "plate")) {
            entries.put("assets/fixture/models/item/" + item + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"fixture:item/" + item + "\"}}");
        }
        entries.put("data/fixture/tags/items/ores.json", "{\"values\":[\"fixture:ore\"]}");
        entries.put("data/fixture/recipes/smelt.json", smelting(200));
        entries.put("data/fixture/recipes/plate.json", """
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"fixture:ingot"}],
                 "result":{"item":"fixture:plate","count":1}}
                """);
        entries.put("data/fixture/recipes/opaque.json", """
                {"type":"fixture:unknown_machine","unknown":{"nested":[1,{"keep":"雪 — сохранить"}]},
                 "conditions":[{"type":"fixture:unimplemented","keep":true}]}
                """);
        writeJar();
    }

    public Path directory() { return directory; }
    public Path jar() { return jar; }
    public String opaqueJson() { return entries.get("data/fixture/recipes/opaque.json"); }

    public String smelting(final int ticks) {
        return """
                {"type":"minecraft:smelting","ingredient":{"item":"fixture:ore"},
                 "result":"fixture:ingot","experience":0.7,"cookingtime":%d}
                """.formatted(ticks);
    }

    /** Mutates only the temporary test fixture to simulate an installed mod changing. */
    public void changeInstalledRecipe(final int ticks) throws IOException {
        entries.put("data/fixture/recipes/smelt.json", smelting(ticks));
        writeJar();
    }

    private void writeJar() throws IOException {
        try (final var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (final var entry : entries.entrySet()) {
                final var member = new ZipEntry(entry.getKey());
                member.setTime(0L);
                zip.putNextEntry(member);
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
