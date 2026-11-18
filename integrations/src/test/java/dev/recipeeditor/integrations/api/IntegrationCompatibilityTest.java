package dev.recipeeditor.integrations.api;
import dev.recipeeditor.integrations.gregtech.GregTechIntegration;
import dev.recipeeditor.core.importing.ModLoaderType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class IntegrationCompatibilityTest {
    private IntegrationCapabilities check(final String mc,final ModLoaderType loader,final String version) { return new GregTechIntegration().capabilities(new IntegrationContext(mc,loader,Map.of("gtceu",version),Map.of())); }
    @Test void numericVersionAndSnapshotBuildIdentityAreDistinct() {
        final var parsed=ModVersion.parse("8.0.0-SNAPSHOT+e24b238");assertEquals(8,parsed.major());assertEquals("e24b238",parsed.build());assertFalse(parsed.release(8,0,0));
        assertEquals(SupportLevel.UNKNOWN,check("1.20.1",ModLoaderType.FORGE,"8.0.0-SNAPSHOT+e24b238").compatibility());
        assertFalse(check("1.20.1",ModLoaderType.FORGE,"8.0.0-SNAPSHOT+other").enabled("Visual Editing"));
        assertEquals(SupportLevel.UNKNOWN,check("1.20.1",ModLoaderType.FORGE,"7.1.40").compatibility());
        assertFalse(check("1.20.1",ModLoaderType.FORGE,"7.1.4+unverified").enabled("Visual Editing"));
        assertEquals(SupportLevel.UNSUPPORTED,check("1.20.1",ModLoaderType.FORGE,"7.1.4").features().get("KubeJS Export"));
    }
    @Test void incompatibleLoaderMinecraftAndUnresolvedVersion() {
        assertEquals(SupportLevel.UNSUPPORTED,check("1.21",ModLoaderType.FORGE,"7.1.4").compatibility());
        assertEquals(SupportLevel.UNSUPPORTED,check("1.20.1",ModLoaderType.FABRIC,"7.1.4").compatibility());
        assertEquals(SupportLevel.UNKNOWN,check("1.20.1",ModLoaderType.FORGE,"${file.jarVersion}").compatibility());
        assertEquals(SupportLevel.UNSUPPORTED,check("1.20.1",ModLoaderType.FORGE,"7.1.4").features().get("CraftTweaker Export"));
    }
}
