package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData.Environment;
import java.util.Objects;

/** Runtime implementations supported by a desktop deployment, independent of static recipe formats. */
public record CompanionRuntimeProfile(String minecraftVersion, String loader, String loaderVersionPrefix) {
    public CompanionRuntimeProfile {
        Objects.requireNonNull(minecraftVersion);
        Objects.requireNonNull(loader);
        if (minecraftVersion.isBlank() || loader.isBlank() || loaderVersionPrefix == null || loaderVersionPrefix.isBlank()) {
            throw new IllegalArgumentException("A runtime profile must specify Minecraft and loader versions");
        }
    }

    public boolean matches(final Environment environment) {
        return minecraftVersion.equals(environment.minecraftVersion()) && loader.equals(environment.loader())
                && environment.loaderVersion().startsWith(loaderVersionPrefix);
    }
}
