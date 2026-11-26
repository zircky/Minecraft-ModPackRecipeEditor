package dev.recipeeditor.integrations;

import dev.recipeeditor.registry.ResourceLayout;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import static dev.recipeeditor.integrations.RecipeFormatAdapter.Generation.*;

/** Selects a format from explicit release metadata. Overlapping adapters fail closed. */
public final class MinecraftCompatibilityRegistry {
    private static final Pattern RELEASE = Pattern.compile("1\\.([0-9]{1,4})(?:\\.[0-9]{1,4})?");
    private final List<RecipeFormatAdapter> adapters;

    public MinecraftCompatibilityRegistry() {
        this(List.of(
                new Minecraft1201Integration(),
                new DiscoveryFormat(LEGACY, version -> inSeries(version, 7, 11), ResourceLayout.noRecipeFiles()),
                new DiscoveryFormat(EARLY_JSON, version -> inSeries(version, 12, 12), new ResourceLayout("assets", "recipes")),
                new DiscoveryFormat(DATAPACK, version -> !version.equals("1.20.1") && inSeries(version, 13, 20),
                        new ResourceLayout("data", "recipes")),
                new DiscoveryFormat(MODERN_DATAPACK, version -> inSeries(version, 21, 9999),
                        new ResourceLayout("data", "recipe"))));
    }

    public MinecraftCompatibilityRegistry(final List<RecipeFormatAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public Optional<RecipeFormatAdapter> find(final String version) {
        final var matches = adapters.stream().filter(adapter -> adapter.supports(version)).toList();
        if (matches.size() != 1) return Optional.empty();
        return Optional.of(matches.getFirst());
    }

    private static boolean inSeries(final String version, final int first, final int last) {
        final var match = RELEASE.matcher(version);
        if (!match.matches()) return false;
        final int minor = Integer.parseInt(match.group(1));
        return minor >= first && minor <= last;
    }

    private record DiscoveryFormat(Generation generation, Predicate<String> versions, ResourceLayout layout)
            implements RecipeFormatAdapter {
        @Override public boolean supports(final String version) { return versions.test(version); }
    }
}
