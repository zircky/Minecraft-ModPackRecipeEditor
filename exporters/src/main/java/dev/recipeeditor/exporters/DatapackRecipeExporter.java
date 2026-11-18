package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.*;
import java.util.*;
public final class DatapackRecipeExporter implements RecipeExporter {
    @Override public ExportFormat format(){return ExportFormat.DATAPACK;}
    @Override public ExportCapability checkCapability(final RecipeChange change,final ExportContext context) {
        if(change.type()==RecipeChangeType.DELETE)return ExportCapability.unsupported("An ordinary datapack cannot universally delete recipes. Select a compatible script exporter.");
        final String error=new VanillaExportSupport().check(change,context);
        return error.isEmpty()?new ExportCapability(ExportSupportStatus.SUPPORTED,"1.20.1 JSON override by recipe ID; pack load priority still matters",Set.of(RecipeChangeType.CREATE,RecipeChangeType.UPDATE),Map.of()):ExportCapability.unsupported(error);
    }
    @Override public Map<String,String> generate(final List<RecipeChange> changes,final ExportContext context) {
        final var files=new TreeMap<String,String>(); if(changes.isEmpty())return files;
        final String root="datapacks/recipe_editor_"+context.profile().id()+"/";
        // Version-scoped adapter. Future versions require another explicit manifest mapping.
        files.put(root+"pack.mcmeta","{\"pack\":{\"pack_format\":15,\"description\":\"Minecraft Recipe Editor generated recipes (1.20.1)\"}}\n");
        for(final var change:changes) {
            if(!checkCapability(change,context).supported())throw new IllegalArgumentException("Unsupported datapack operation");
            files.put(root+"data/"+change.id().namespace()+"/recipes/"+change.id().path()+".json",change.replacement().getFirst().rawJson()+"\n");
        } return files;
    }
}
