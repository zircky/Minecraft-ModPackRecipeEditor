package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.Path;
import java.util.*;

/** Shared by the planner and wizard: saved bases and derived-from locations are all retained. */
public final class ExportSourceMapping {
    public RecipeProvenance resolve(final RecipeChange change,final RecipeIndex index,final Path project) {
        return resolve(change,index,project,false);
    }
    public RecipeProvenance resolve(final RecipeChange change,final RecipeIndex index,final Path project,final boolean blocked) {
        final var bases=change.originals().stream().map(OriginalRecipeReference::source).toList();
        final var derived=new LinkedHashSet<RecipeSource>();
        index.findById(change.id()).forEach(recipe->derived.add(recipe.source()));
        change.replacement().forEach(recipe->derived.add(recipe.source()));
        return new RecipeSourceResolver().project(change.id(),bases,List.copyOf(derived),project,change.type()==RecipeChangeType.DELETE,blocked);
    }
}
