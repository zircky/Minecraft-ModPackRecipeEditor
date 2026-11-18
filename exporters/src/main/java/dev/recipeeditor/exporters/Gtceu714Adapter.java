package dev.recipeeditor.exporters;
import dev.recipeeditor.integrations.gregtech.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.util.*;
/** Phase 5 adapter entry point retained; Phase 7 owns the typed codec profile. */
public final class Gtceu714Adapter {
    public boolean recognizes(final RecipeChange change, final ExportContext context) {
        return context.definitions(change).stream().anyMatch(r -> r.type().location().map(id -> id.namespace().equals("gtceu")).orElse(false));
    }
    public ExportCapability check(final RecipeChange change, final ExportContext context) {
        if (!context.modVersion("gtceu").equals("7.1.4")) return ExportCapability.unsupported("GTCEu export requires verified 7.1.4; installed=" + context.modVersion("gtceu"));
        final var properties = new LinkedHashMap<String, ExportSupportStatus>();
        for (final String name : List.of("Recipe Type", "Item Inputs", "Item Outputs", "Fluid Inputs", "Fluid Outputs", "EU/t", "Duration", "Chance Outputs", "Non-Consumable Inputs")) properties.put(name, ExportSupportStatus.SUPPORTED);
        for (final String name : List.of("Recipe Conditions", "Research Requirements", "Additional Properties")) properties.put(name, ExportSupportStatus.UNSUPPORTED);
        try {
            if (context.definitions(change).isEmpty()) throw new IllegalArgumentException("No GT definition");
            for (final var definition : context.definitions(change)) {
                final var model = new GregTechRecipeMapper().map(definition);
                final var unsupported = new GregTechRecipeExporter().unsupported(model);
                if (!unsupported.isEmpty()) throw new IllegalArgumentException(unsupported.toString());
            }
            return new ExportCapability(ExportSupportStatus.SUPPORTED, "GTCEu 7.1.4 checked codec profile via GT KubeJS schema factory; runtime verification is a separate operation", Set.of(RecipeChangeType.CREATE, RecipeChangeType.UPDATE, RecipeChangeType.DELETE), properties);
        } catch (RuntimeException failure) { return new ExportCapability(ExportSupportStatus.UNSUPPORTED, failure.getMessage(), Set.of(), properties); }
    }
}
