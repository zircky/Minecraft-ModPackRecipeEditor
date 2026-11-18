package dev.recipeeditor.exporters;

import java.util.*;
public record ExportPlan(ExportRequest request,List<PlannedRecipe> recipes,Map<String,String> files,Map<String,String> expectedHashes,Set<String> obsoleteFiles,List<ExportDiagnostic> diagnostics) {
    public ExportPlan {recipes=List.copyOf(recipes);files=Collections.unmodifiableMap(new TreeMap<>(files));expectedHashes=Map.copyOf(expectedHashes);obsoleteFiles=Set.copyOf(obsoleteFiles);diagnostics=List.copyOf(diagnostics);}
    public boolean valid() {return diagnostics.stream().noneMatch(d->d.level()==ExportDiagnostic.Level.ERROR)&&recipes.stream().allMatch(r->r.capability().supported());}
}
