package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.*;
import java.util.*;

/** KUBEJS_DATA mechanism, selected through the existing KUBEJS export format. */
public final class KubeJsDataExporter implements RecipeExporter {
    @Override public ExportFormat format(){return ExportFormat.KUBEJS;}
    @Override public ExportCapability checkCapability(final RecipeChange change,final ExportContext context){
        final String version=new KubeJsCompatibility().check(context);if(!version.isEmpty())return ExportCapability.unsupported(version);
        if(change.type()==RecipeChangeType.DELETE)return ExportCapability.unsupported("KUBEJS_DATA cannot delete recipes with ordinary JSON. Choose JAVASCRIPT, or explicitly select AUTO/COMBINED.");
        final String error=new VanillaExportSupport().checkKubeJs(change,context);
        if(!error.isEmpty())return ExportCapability.unsupported(error+"; KUBEJS_DATA supports only checked Vanilla JSON serializers, not programmatic/custom GT recipes");
        try{targetFile(change,context);}catch(IllegalArgumentException invalid){return ExportCapability.unsupported(invalid.getMessage());}
        return new ExportCapability(ExportSupportStatus.SUPPORTED,"KUBEJS_DATA: original JSON, exact ID path. Known Vanilla semantics checked; ordinary unknown root fields retained but ignored by Vanilla; other scripts and pack priorities may override this file.",Set.of(RecipeChangeType.CREATE,RecipeChangeType.UPDATE),Map.of());
    }
    public String targetFile(final RecipeChange change,final ExportContext context){return "kubejs/"+new RecipeDataPathResolver().resolve(context.modpack().minecraftVersion().value(),change.id());}
    @Override public Map<String,String> generate(final List<RecipeChange> changes,final ExportContext context){
        final var files=new TreeMap<String,String>();
        for(final var change:changes){
            final var capability=checkCapability(change,context);if(!capability.supported())throw new IllegalArgumentException(capability.reason());
            if(files.putIfAbsent(targetFile(change,context),change.replacement().getFirst().rawJson()+"\n")!=null)throw new IllegalArgumentException("Duplicate KUBEJS_DATA target for "+change.id());
        }
        return files;
    }
}
