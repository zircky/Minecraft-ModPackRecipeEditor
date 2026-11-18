package dev.recipeeditor.integrations.api;
import dev.recipeeditor.integrations.gregtech.GregTechIntegration;
import dev.recipeeditor.core.importing.ModLoaderType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class IntegrationRegistryTest {
    private IntegrationContext context(final String version) { return new IntegrationContext("1.20.1",ModLoaderType.FORGE,version.isEmpty()?Map.of():Map.of("gtceu",version),Map.of()); }
    @Test void registrationCapabilitiesAndDisabling() {
        final var registry=new IntegrationRegistry();registry.register(new GregTechIntegration());
        assertEquals(List.of("gtceu"),registry.modIds());assertTrue(registry.capabilities("gtceu",context("7.1.4")).enabled("Visual Editing"));
        registry.enabled("gtceu",false);assertFalse(registry.capabilities("gtceu",context("7.1.4")).enabled("Visual Editing"));
        registry.enabled("gtceu",true);assertTrue(registry.capabilities("gtceu",context("7.1.4")).enabled("Visual Editing"));
    }
    @Test void absentModAndConflictingAdapterFailSafely() {
        final var registry=new IntegrationRegistry();registry.register(new GregTechIntegration());
        assertEquals(SupportLevel.UNSUPPORTED,registry.capabilities("gtceu",context("")).compatibility());
        assertThrows(IllegalArgumentException.class,()->registry.register(new GregTechIntegration()));
        assertThrows(IllegalArgumentException.class,()->registry.enabled("unknown",false));
    }
}
