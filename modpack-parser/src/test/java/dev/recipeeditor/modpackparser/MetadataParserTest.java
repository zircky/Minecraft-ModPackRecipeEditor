package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

final class MetadataParserTest {
    private ModMetadataParseResult parse(final ModMetadataParser parser,final String path,final String text) {
        return parser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),new MetadataContext(path,Map.of("Implementation-Version","2.3")));
    }
    @Test void forgeTomlAndManifestVersion() {
        final var value = parse(new ForgeMetadataParser(),"META-INF/mods.toml",TestFixtures.forge("sample").replace("1.0","${file.jarVersion}")
                + "[[dependencies.sample]]\nmodId='forge'\nmandatory=true\nversionRange='[47,)'\n");
        assertEquals("2.3",value.mods().getFirst().version());
        assertEquals(DependencyKind.REQUIRED,value.mods().getFirst().dependencies().getFirst().kind());
    }
    @Test void neoForgeTypesAndLegacyPath() {
        final String toml = TestFixtures.forge("sample") + "[[dependencies.sample]]\nmodId='neoforge'\ntype='incompatible'\nversionRange='[21,)'";
        final var value = parse(new NeoForgeMetadataParser(),"META-INF/neoforge.mods.toml",toml);
        assertEquals(DependencyKind.INCOMPATIBLE,value.mods().getFirst().dependencies().getFirst().kind());
        assertEquals(ModLoaderType.NEOFORGE,parse(new ForgeMetadataParser(),"META-INF/mods.toml",toml).mods().getFirst().loaderCompatibility());
    }
    @Test void fabricJsonDependenciesAndAuthors() {
        final var value = parse(new FabricMetadataParser(),"fabric.mod.json",TestFixtures.fabric().replace("\"name\":\"Sample\"","\"authors\":[{\"name\":\"Author\"}],\"depends\":{\"minecraft\":\"~1.20.1\"},\"breaks\":{\"bad\":\"*\"}"));
        assertEquals("Author",value.mods().getFirst().authors().getFirst()); assertEquals(2,value.mods().getFirst().dependencies().size());
    }
    @Test void quiltJson() {
        final var value = parse(new QuiltMetadataParser(),"quilt.mod.json","{\"schema_version\":1,\"quilt_loader\":{\"id\":\"sample\",\"version\":\"1\",\"metadata\":{\"name\":\"Quilt Sample\",\"contributors\":{\"Someone\":\"Author\"}},\"depends\":[{\"id\":\"minecraft\",\"versions\":\"*\"}]}}");
        assertEquals("Someone",value.mods().getFirst().authors().getFirst()); assertEquals(ModLoaderType.QUILT,value.mods().getFirst().loaderCompatibility());
    }
    @Test void malformedInputsProduceDiagnostics() {
        for (final var parser : java.util.List.of(new ForgeMetadataParser(),new NeoForgeMetadataParser(),new FabricMetadataParser(),new QuiltMetadataParser())) {
            final var result = parse(parser,"test","not valid [{");
            assertTrue(result.mods().isEmpty()); assertEquals(ScanStatus.ERROR,result.diagnostics().getFirst().severity());
        }
    }
    @Test void complexQuiltDependencyIsNotTreatedAsUnconditional() {
        final var value = parse(new QuiltMetadataParser(),"quilt.mod.json","{\"schema_version\":1,\"quilt_loader\":{\"id\":\"sample\",\"version\":\"1\",\"depends\":[[{\"id\":\"a\"},{\"id\":\"b\"}]]}}");
        assertEquals(DependencyKind.UNKNOWN,value.mods().getFirst().dependencies().getFirst().kind()); assertFalse(value.diagnostics().isEmpty());
    }
    @Test void unscopedDependenciesDoNotDiscardModIdentity() {
        final var result = parse(new ForgeMetadataParser(),"META-INF/mods.toml",TestFixtures.forge("sample")
                + "[[dependencies]]\nmodId='other'\nmandatory=true");
        assertEquals("sample",result.mods().getFirst().id());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("Nonstandard")));
    }
}
