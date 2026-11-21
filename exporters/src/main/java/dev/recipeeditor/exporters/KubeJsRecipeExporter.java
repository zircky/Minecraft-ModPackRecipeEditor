package dev.recipeeditor.exporters;

import dev.recipeeditor.recipeengine.editor.*;
import java.util.*;

/** One ChangeSet and one mechanism per ID. AUTO/COMBINED never create the same ID twice. */
public final class KubeJsRecipeExporter implements RecipeExporter {
    private final KubeJsJavaScriptExporter javascript=new KubeJsJavaScriptExporter();
    private final KubeJsDataExporter data=new KubeJsDataExporter();
    @Override public ExportFormat format(){return ExportFormat.KUBEJS;}
    public KubeJsRecipePlan plan(final RecipeChange change,final ExportContext context){
        final var selected=context.profile().kubeJsMode();
        final var dataCapability=data.checkCapability(change,context);
        final boolean useData=selected==KubeJsExportMode.DATA_JSON||((selected==KubeJsExportMode.AUTO||selected==KubeJsExportMode.COMBINED)&&dataCapability.supported());
        final var capability=useData?dataCapability:javascript.checkCapability(change,context);
        return new KubeJsRecipePlan(useData?KubeJsRecipePlan.Mechanism.KUBEJS_DATA:KubeJsRecipePlan.Mechanism.KUBEJS_JAVASCRIPT,capability,
                capability.supported()?List.of(useData?data.targetFile(change,context):javascript.targetFile(context)):List.of());
    }
    @Override public ExportCapability checkCapability(final RecipeChange change,final ExportContext context){return plan(change,context).capability();}
    @Override public Map<String,String> generate(final List<RecipeChange> changes,final ExportContext context){
        final var ids=new HashSet<dev.recipeeditor.core.ResourceLocation>();final var js=new ArrayList<RecipeChange>();final var json=new ArrayList<RecipeChange>();
        for(final var change:changes){
            if(!ids.add(change.id()))throw new IllegalArgumentException("Duplicate KubeJS operation for "+change.id());
            final var plan=plan(change,context);if(!plan.capability().supported())throw new IllegalArgumentException(plan.capability().reason());
            (plan.mechanism()==KubeJsRecipePlan.Mechanism.KUBEJS_DATA?json:js).add(change);
        }
        final var files=new TreeMap<String,String>(data.generate(json,context));
        javascript.generate(js,context).forEach((path,text)->{if(files.putIfAbsent(path,text)!=null)throw new IllegalArgumentException("Conflicting KubeJS output: "+path);});
        return files;
    }
}
