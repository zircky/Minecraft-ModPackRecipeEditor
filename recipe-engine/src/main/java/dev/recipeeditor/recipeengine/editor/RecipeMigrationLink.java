package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import java.util.Map;

/** A replacement with a required new ID is two linked project operations. */
public final class RecipeMigrationLink {
    public static final String PEER = "mre.migration.peer";

    private RecipeMigrationLink() {}

    public static boolean valid(final RecipeChange change, final Map<ResourceLocation,RecipeChange> changes) {
        final String peer=change.metadata().get(PEER);
        if(peer==null)return true;
        try {
            final var linked=changes.get(ResourceLocation.parse(peer));
            return linked!=null && change.id().toString().equals(linked.metadata().get(PEER))
                    && ((change.type()==RecipeChangeType.CREATE&&linked.type()==RecipeChangeType.DELETE)
                    || (change.type()==RecipeChangeType.DELETE&&linked.type()==RecipeChangeType.CREATE));
        } catch(IllegalArgumentException invalid) { return false; }
    }
}
