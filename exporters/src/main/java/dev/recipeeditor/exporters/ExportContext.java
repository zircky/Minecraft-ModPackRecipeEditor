package dev.recipeeditor.exporters;

import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.Path;
import java.util.*;
public record ExportContext(ModpackDefinition modpack,RecipeIndex originals,RecipeChangeSet savedChanges,Path projectFile,
        ExportProfile profile,List<ExportManifest> previousManifests,Set<dev.recipeeditor.core.ResourceLocation> blockedIds,boolean allowSourceDirectory) {
    public ExportContext { previousManifests=List.copyOf(previousManifests);blockedIds=Set.copyOf(blockedIds); }
    public String modVersion(final String id) {
        final var values=modpack.mods().stream().filter(m->m.metadata().id().equals(id)).map(m->m.metadata().version()).distinct().toList();
        return values.size()==1?values.getFirst():"UNKNOWN";
    }
    public List<RecipeDefinition> definitions(final RecipeChange change) {return change.replacement().isEmpty()?originals.findById(change.id()):change.replacement();}
}
