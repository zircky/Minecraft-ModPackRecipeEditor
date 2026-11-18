package dev.recipeeditor.recipeengine;

import dev.recipeeditor.registry.*;
import dev.recipeeditor.core.importing.ImportDiagnostic;
import java.nio.file.Path;
import java.util.List;
public record DiscoveryResult(Path selectedRoot,RegistryIndex registry,RecipeIndex recipes,List<ImportDiagnostic> diagnostics,long elapsedMillis) {
    public DiscoveryResult { diagnostics=List.copyOf(diagnostics); }
}
