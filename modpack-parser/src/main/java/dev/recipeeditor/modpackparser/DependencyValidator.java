package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.io.SafeFiles;

import dev.recipeeditor.core.importing.*;
import java.util.*;

/** Checks presence only. Loader-specific version expressions are explicitly left unevaluated. */
public final class DependencyValidator {
    public List<ImportDiagnostic> validate(final List<ModDefinition> mods, final ModLoaderDefinition loader) {
        final var diagnostics = new ArrayList<ImportDiagnostic>();
        final var ids = new HashSet<String>();
        for (final var mod : mods) if (!mod.metadata().id().isBlank() && !ids.add(mod.metadata().id()))
            diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,mod.metadata().id(),"Duplicate mod ID; may be alternative metadata in a multi-loader JAR"));
        final var builtins = new HashSet<>(Set.of("minecraft", "java"));
        switch (loader.type()) {
            case FORGE -> builtins.addAll(Set.of("forge", "fml"));
            case NEOFORGE -> builtins.addAll(Set.of("neoforge", "fml"));
            case FABRIC -> builtins.add("fabricloader");
            case QUILT -> builtins.addAll(Set.of("quilt_loader", "fabricloader"));
            default -> { }
        }
        for (final var mod : mods) {
            SafeFiles.checkCancelled();
            for (final var dependency : mod.metadata().dependencies()) {
                final String source = mod.metadata().id() + " -> " + dependency.targetId();
                final boolean present = ids.contains(dependency.targetId()) || builtins.contains(dependency.targetId());
                if (dependency.kind() == DependencyKind.REQUIRED && !present)
                    diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, source,
                            "Required dependency not found among scanned mods; may be nested, aliased or provided by the loader"));
                if (!dependency.versionConstraint().isBlank() && !dependency.versionConstraint().equals("*"))
                    diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, source, "Version constraint not evaluated: " + dependency.versionConstraint()));
                if (dependency.kind() == DependencyKind.INCOMPATIBLE && present)
                    diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING, source, "Potential incompatible dependency is present; its version condition was not evaluated"));
            }
        }
        return List.copyOf(diagnostics);
    }
}
