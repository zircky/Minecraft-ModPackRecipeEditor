package dev.recipeeditor.app;

import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("external")
final class RealStaticAnalysisTest {
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void realForge1201Instance() throws Exception {
        final var directory=Path.of(System.getProperty("realModpack"));
        try(final var importer=new DefaultModpackImportService(); final var analyzer=new DefaultStaticAnalysisService()) {
            final var pack=importer.start(directory,p->{}).result().get(2,TimeUnit.MINUTES).modpack();
            final var snapshots=new HashMap<Path,String>();
            for(final var jar:pack.jars()) snapshots.put(jar.sourceJar(),Files.size(jar.sourceJar())+":"+Files.getLastModifiedTime(jar.sourceJar()));
            final var result=analyzer.start(pack,p->{}).result().get(5,TimeUnit.MINUTES);
            assertFalse(result.registry().entries().isEmpty()); assertFalse(result.recipes().recipes().isEmpty());
            assertTrue(result.registry().entries().stream().noneMatch(e->e.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME));
            for(final var entry:snapshots.entrySet()) assertEquals(entry.getValue(),Files.size(entry.getKey())+":"+Files.getLastModifiedTime(entry.getKey()));
            final String summary="JARs="+pack.jars().size()+"; items="+result.registry().entries(ResourceKind.ITEM).size()+"; blocks="+result.registry().entries(ResourceKind.BLOCK).size()
                +"; fluids="+result.registry().entries(ResourceKind.FLUID).size()+"; recipes="+result.recipes().recipes().size()+"; status="
                +result.recipes().recipes().stream().collect(java.util.stream.Collectors.groupingBy(RecipeDefinition::status,java.util.stream.Collectors.counting()))
                +"; conflicts="+result.recipes().conflicts().size()+"; diagnostics="+result.diagnostics().size()+"; ms="+result.elapsedMillis();
            Files.createDirectories(Path.of("build")); Files.writeString(Path.of("build/real-static-analysis.txt"),summary+"\n"+result.diagnostics().stream().filter(d->d.severity()==dev.recipeeditor.core.importing.ScanStatus.ERROR).limit(100).map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
            System.out.println(summary);
        }
    }
}
