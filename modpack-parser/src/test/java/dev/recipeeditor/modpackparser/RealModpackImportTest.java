package dev.recipeeditor.modpackparser;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import dev.recipeeditor.core.importing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("external")
final class RealModpackImportTest {
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void importsRealForge1201InstanceReadOnly() throws Exception {
        try (final var service = new DefaultModpackImportService()) {
            final var result = service.start(Path.of(System.getProperty("realModpack")),p -> {}).result().get(3,TimeUnit.MINUTES);
            assertEquals("1.20.1",result.modpack().minecraftVersion().value());
            assertEquals(ModLoaderType.FORGE,result.modpack().loader().type());
            assertFalse(result.modpack().mods().isEmpty());
            final var pack = result.modpack();
            final String summary = "Minecraft=" + pack.minecraftVersion().value() + "; Loader=" + pack.loader().type() + " " + pack.loader().version()
                    + "; JARs=" + pack.jars().size() + "; definitions=" + pack.mods().size() + "; errorJars="
                    + pack.jars().stream().filter(j -> j.status()==ScanStatus.ERROR).count() + "; diagnostics=" + result.diagnostics().size();
            System.out.println(summary);
            Files.createDirectories(Path.of("build"));
            Files.writeString(Path.of("build/real-import-result.txt"),summary + "\n" + result.diagnostics().stream()
                    .filter(d -> d.severity()==ScanStatus.ERROR).map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
        }
    }
}
