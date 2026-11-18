package dev.recipeeditor.integrations.gregtech;
import dev.recipeeditor.integrations.api.*;
import dev.recipeeditor.core.importing.ModLoaderType;
import java.util.*;
public final class GregTechIntegration implements ModIntegration {
    @Override public String modId() { return "gtceu"; }
    @Override public IntegrationCapabilities capabilities(final IntegrationContext context) {
        if (!context.mods().containsKey(modId())) return unavailable(SupportLevel.UNSUPPORTED, "GTCEu is not installed");
        if (!context.minecraftVersion().equals("1.20.1") || context.loader() != ModLoaderType.FORGE)
            return unavailable(SupportLevel.UNSUPPORTED, "Requires Minecraft 1.20.1 Forge");
        try {
            final var version = ModVersion.parse(context.mods().get(modId()));
            if (!version.release(7, 1, 4)) {
                final String build = version.build().isEmpty() ? "no build identity" : "build=" + version.build();
                return unavailable(SupportLevel.UNKNOWN, "Unverified GTCEu ABI/schema: " + context.mods().get(modId()) + " (" + build + "). 8.0 changes Content fields/chance semantics; editing/export disabled.");
            }
            return new IntegrationCapabilities(SupportLevel.SUPPORTED, "gtceu-7.1.4/v2",
                    Map.of("Runtime Extraction", SupportLevel.PARTIALLY_SUPPORTED, "Recipe Mapping", SupportLevel.SUPPORTED,
                           "Visual Editing", SupportLevel.SUPPORTED, "KubeJS Export", "2001.6.4-build.120".equals(context.mods().get("kubejs")) ? SupportLevel.PARTIALLY_SUPPORTED : SupportLevel.UNSUPPORTED,
                           "Datapack Export", SupportLevel.UNSUPPORTED, "CraftTweaker Export", SupportLevel.UNSUPPORTED),
                    List.of("Per-recipe validation/export checks remain required. Machine slot limits need runtime evidence; unknown properties are preserved but block export.",
                            "KubeJS dependency/version is checked separately by the exporter. Runtime state is not original source JSON."));
        } catch (IllegalArgumentException unknown) { return unavailable(SupportLevel.UNKNOWN, unknown.getMessage()); }
    }
    private IntegrationCapabilities unavailable(final SupportLevel level, final String message) {
        return new IntegrationCapabilities(level, "gtceu-7.1.4/v2", Map.of(), List.of(message));
    }
}
