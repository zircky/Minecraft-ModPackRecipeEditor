package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.SourceType;
import java.nio.file.Path;
import java.util.*;
public record ProjectDocument(int version,Path modpackDirectory,List<StoredRecipeChange> changes) {
    public ProjectDocument { if(version!=1) throw new IllegalArgumentException("Unsupported project format version: "+version); Objects.requireNonNull(modpackDirectory); if(!modpackDirectory.isAbsolute()) throw new IllegalArgumentException("Absolute source directory required"); changes=List.copyOf(changes);
        if(changes.size()>10000 || changes.stream().map(StoredRecipeChange::id).distinct().count()!=changes.size()) throw new IllegalArgumentException("Invalid/duplicate change IDs"); }
    public static ProjectDocument capture(final Path modpack,final RecipeChangeSet changes) {
        return new ProjectDocument(1,modpack,changes.changes().values().stream().map(change->new StoredRecipeChange(change.id(),change.type(),change.originals(),
                change.replacement().stream().map(RecipeDefinition::rawJson).toList(),change.timestamp(),change.metadata())).toList());
    }
    public RecipeChangeSet materialize(final RecipeValidator validator,final Path projectFile) {
        final var values=new LinkedHashMap<dev.recipeeditor.core.ResourceLocation,RecipeChange>();
        for(final var change:changes) {
            if(values.containsKey(change.id())) throw new IllegalArgumentException("Duplicate change ID");
            final var source=new RecipeSource(SourceType.LOCAL_RESOURCE,List.of(),projectFile,"changes/"+change.id(),"User project overlay");
            final var replacements=change.replacementJson().stream().map(raw->{ final var validation=validator.validate(change.id(),raw,source);
                if(!validation.valid()) throw new IllegalArgumentException("Invalid saved recipe: "+change.id()); return validation.recipe(); }).toList();
            values.put(change.id(),new RecipeChange(change.id(),change.type(),change.originals(),replacements,change.timestamp(),change.metadata()));
        }
        return new RecipeChangeSet(values);
    }
}
