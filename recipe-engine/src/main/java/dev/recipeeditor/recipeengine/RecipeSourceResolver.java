package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.SourceType;
import java.nio.file.Path;
import java.util.*;

/** Resolves evidence only; filesystem traversal order and namespace never establish a winner. */
public final class RecipeSourceResolver {
    public RecipeProvenance discover(final ResourceLocation id,final List<RecipeSource> sources) {
        return new RecipeProvenance(id,RecipeProvenance.Scope.STATIC_DISCOVERY,sources,Optional.empty(),List.of(),
                sources.isEmpty()?ProvenanceConfidence.UNKNOWN:ProvenanceConfidence.PHYSICAL_LOCATION_ONLY);
    }
    public RecipeProvenance project(final ResourceLocation id,final List<RecipeSource> originals,
            final List<RecipeSource> derivedFrom,final Path projectFile,final boolean deleted,final boolean blocked) {
        final var all=new LinkedHashSet<>(originals); all.addAll(derivedFrom);
        final var overlay=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),projectFile==null?Path.of(""):projectFile,"changes/"+id,"User project overlay; not installed into Minecraft");
        if(blocked)return new RecipeProvenance(id,RecipeProvenance.Scope.PROJECT_CONFLICT,List.copyOf(all),Optional.empty(),List.of(),ProvenanceConfidence.UNKNOWN);
        return new RecipeProvenance(id,RecipeProvenance.Scope.PROJECT,List.copyOf(all),deleted?Optional.empty():Optional.of(overlay),originals,ProvenanceConfidence.PROJECT_OVERLAY);
    }
    public RecipeProvenance runtime(final ResourceLocation id,final List<RecipeSource> candidates,final RecipeSource observed) {
        if(observed.type()!=SourceType.RUNTIME)throw new IllegalArgumentException("Runtime observation required");
        return new RecipeProvenance(id,RecipeProvenance.Scope.RUNTIME,candidates,Optional.of(observed),List.of(),ProvenanceConfidence.RUNTIME_OBSERVED_ORIGIN_UNKNOWN);
    }
}
