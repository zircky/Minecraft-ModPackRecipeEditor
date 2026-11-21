package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.OriginalRecipeReference;
import dev.recipeeditor.recipeengine.editor.RecipeChange;
import dev.recipeeditor.recipeengine.editor.RecipeChangeType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves format adapters against project state without generating files or guessing ownership. */
public final class ExportCapabilityResolver {
    private final Map<ExportFormat, RecipeExporter> exporters;

    public ExportCapabilityResolver(final List<RecipeExporter> exporters) {
        this.exporters = exporters.stream().collect(Collectors.toUnmodifiableMap(RecipeExporter::format, Function.identity()));
    }

    public ExportCapability resolve(final RecipeChange change, final ExportContext context, final ExportFormat format) {
        Objects.requireNonNull(change);
        Objects.requireNonNull(context);
        Objects.requireNonNull(format);
        final String conflict = sourceConflict(change, context);
        if (!conflict.isEmpty()) return ExportCapability.unsupported(conflict);
        final var exporter = exporters.get(format);
        if (exporter == null) return new ExportCapability(ExportSupportStatus.UNKNOWN,
                "No capability adapter registered for " + format, java.util.Set.of(), Map.of());
        final var capability = exporter.checkCapability(change, context);
        if (capability.supported() && !capability.operations().contains(change.type()))
            return ExportCapability.unsupported("Adapter does not support operation " + change.type());
        return capability;
    }

    private String sourceConflict(final RecipeChange change, final ExportContext context) {
        if (!context.modpack().minecraftVersion().value().equals(context.profile().minecraftVersion())
                || !context.modpack().loader().type().name().equals(context.profile().loader()))
            return "Export profile version/loader does not match the imported instance";
        if (context.blockedIds().contains(change.id())) return "Source change is quarantined; resolve the source conflict before export";
        if (change.type() == RecipeChangeType.CREATE) return "";
        final var current = context.originals().findById(change.id()).stream().map(OriginalRecipeReference::from).toList();
        return current.isEmpty() || !new HashSet<>(current).equals(new HashSet<>(change.originals()))
                ? "Original recipe definitions are missing or changed since this change was saved" : "";
    }
}
