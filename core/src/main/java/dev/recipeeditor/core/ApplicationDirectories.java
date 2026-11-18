package dev.recipeeditor.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Windows user data locations, independent of the install directory and working directory. */
public record ApplicationDirectories(Path roaming, Path local, Path legacy) {
    public ApplicationDirectories {
        roaming = absolute(roaming);
        local = absolute(local);
        legacy = absolute(legacy);
    }

    public static ApplicationDirectories current() {
        return resolve(System.getenv(), Path.of(System.getProperty("user.home")));
    }

    public static ApplicationDirectories resolve(final Map<String, String> environment, final Path home) {
        final var userHome = absolute(home);
        final String override = environment.getOrDefault("MRE_DATA_HOME", "");
        if (!override.isBlank()) {
            final var base = absolute(Path.of(override));
            return new ApplicationDirectories(base.resolve("roaming"), base.resolve("local"),
                    userHome.resolve(".minecraft-recipe-editor"));
        }
        return new ApplicationDirectories(
                environmentPath(environment, "APPDATA", userHome.resolve("AppData/Roaming")).resolve("MinecraftRecipeEditor"),
                environmentPath(environment, "LOCALAPPDATA", userHome.resolve("AppData/Local")).resolve("MinecraftRecipeEditor"),
                userHome.resolve(".minecraft-recipe-editor"));
    }

    public Path settings() { return roaming.resolve("settings.json"); }
    public Path profiles() { return roaming.resolve("profiles"); }
    public Path uiState() { return roaming.resolve("ui-state.json"); }
    public Path cache() { return local.resolve("cache"); }
    public Path logs() { return local.resolve("logs"); }
    public Path snapshots() { return local.resolve("snapshots"); }
    public Path temporary() { return local.resolve("temp"); }

    public void create() throws IOException {
        for (final var path : List.of(roaming, profiles(), cache(), logs(), snapshots(), temporary())) {
            Files.createDirectories(path);
        }
    }

    private static Path environmentPath(final Map<String, String> environment, final String name, final Path fallback) {
        final String value = environment.getOrDefault(name, "");
        return value.isBlank() ? fallback : absolute(Path.of(value));
    }

    private static Path absolute(final Path path) {
        Objects.requireNonNull(path);
        if (!path.isAbsolute()) throw new IllegalArgumentException("Application data paths must be absolute");
        return path.normalize();
    }
}
