package dev.recipeeditor.integrations.api;
import dev.recipeeditor.core.importing.*;
import java.util.*;
public record IntegrationContext(String minecraftVersion, ModLoaderType loader, Map<String, String> mods,
                                 Map<String, String> artifactHashes) {
    public IntegrationContext { mods = Map.copyOf(mods); artifactHashes = Map.copyOf(artifactHashes); }
    public static IntegrationContext from(final ModpackDefinition pack) {
        final var mods = new LinkedHashMap<String, String>();
        pack.mods().forEach(mod -> mods.merge(mod.metadata().id(), mod.metadata().version(),
                (first, second) -> first.equals(second) ? first : "AMBIGUOUS"));
        return new IntegrationContext(pack.minecraftVersion().value(), pack.loader().type(), mods, Map.of());
    }
}
