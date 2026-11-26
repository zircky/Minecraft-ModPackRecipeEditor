package dev.recipeeditor.integrations.gregtech;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.api.IntegrationContext;
import java.util.*;

/** Changes only the type property and validates the untouched payload against observed target IO. */
public final class RecipeTypeMigrationService {
    public record Result(String json, List<String> issues) {
        public Result { issues=List.copyOf(issues); }
        public boolean compatible() { return issues.isEmpty(); }
    }

    public Result preview(final String source, final ResourceLocation targetId, final GregTechMachineCatalog.Machine target,
            final IntegrationContext context) {
        final var mapper=new GregTechRecipeMapper();
        final var root=(ObjectNode)mapper.object(source).deepCopy();
        root.put("type",target.id().toString());
        final var issues=new ArrayList<String>();
        if(!new GregTechIntegration().capabilities(context).enabled("Visual Editing")
                || !"7.1.4".equals(target.observedVersion())) issues.add("The target lacks a verified GTCEu 7.1.4 editing profile and captured limits.");
        if(!"2001.6.4-build.120".equals(context.mods().get("kubejs"))) issues.add("The target lacks the verified KubeJS build.");
        if(root.has("category")) issues.add("Check or remove the explicit source category before migrating. Its validity on the target is unknown.");
        final var model=mapper.map(targetId,root.toString(),false);
        final var counts=new TreeMap<String,Integer>();
        for(final var content:model.contents()) {
            if(content.role().equals("inputs")||content.role().equals("outputs"))
                counts.merge(content.role()+"."+content.capability(),1,Integer::sum);
        }
        counts.forEach((key,count)->{
            final var limit=target.limits().get(key);
            if(limit==null)issues.add("Unknown "+key+" limit for "+target.id());
            else if(count>limit)issues.add(key+" contains "+count+" entries; the target permits "+limit);
        });
        issues.addAll(new GregTechRecipeExporter().unsupported(model));
        return new Result(root.toString(),issues);
    }
}
