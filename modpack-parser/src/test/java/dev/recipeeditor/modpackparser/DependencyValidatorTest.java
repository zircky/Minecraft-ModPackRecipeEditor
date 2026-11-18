package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

final class DependencyValidatorTest {
    private ModDefinition mod(final String id, final List<ModDependency> dependencies) {
        return new ModDefinition(new ModMetadata(id,id,"1","",List.of(),dependencies,ModLoaderType.FORGE,"test",""),Path.of("test.jar"),ScanStatus.OK,List.of());
    }
    private List<ImportDiagnostic> validate(final DependencyKind kind,final String constraint, final boolean present) {
        final var mods = new ArrayList<ModDefinition>(); mods.add(mod("sample",List.of(new ModDependency("target",kind,constraint,""))));
        if (present) mods.add(mod("target",List.of()));
        return new DependencyValidator().validate(mods,new ModLoaderDefinition(ModLoaderType.FORGE,"47",List.of()));
    }
    @Test void requiredPresent() { assertTrue(validate(DependencyKind.REQUIRED,"*",true).isEmpty()); }
    @Test void requiredMissing() { assertTrue(validate(DependencyKind.REQUIRED,"*",false).getFirst().message().contains("may be nested")); }
    @Test void optionalMissing() { assertTrue(validate(DependencyKind.OPTIONAL,"*",false).isEmpty()); }
    @Test void unsupportedConstraint() { assertTrue(validate(DependencyKind.REQUIRED,"[1,2)",true).getFirst().message().contains("not evaluated")); }
    @Test void gameAndLoaderAreBuiltin() {
        final var diagnostics = new DependencyValidator().validate(List.of(mod("sample",List.of(new ModDependency("minecraft",DependencyKind.REQUIRED,"*",""),
                new ModDependency("forge",DependencyKind.REQUIRED,"*","")))),new ModLoaderDefinition(ModLoaderType.FORGE,"47",List.of()));
        assertTrue(diagnostics.isEmpty());
    }
}
