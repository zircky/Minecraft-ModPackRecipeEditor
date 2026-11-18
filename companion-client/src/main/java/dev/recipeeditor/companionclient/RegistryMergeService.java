package dev.recipeeditor.companionclient;

import dev.recipeeditor.companionprotocol.RuntimeData;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;

public final class RegistryMergeService {
    public record Result(RegistryIndex preferred,RegistryIndex staticOriginals,TagIndex runtimeTags,List<String> diagnostics) {public Result{diagnostics=List.copyOf(diagnostics);}}
    public Result merge(final RegistryIndex original,final RuntimeData.Snapshot snapshot,final Path snapshotFile) {
        if(!snapshot.complete())throw new IllegalArgumentException("Cannot merge partial runtime snapshot");
        final var entries=new LinkedHashMap<String,RegistryEntry>();original.entries().forEach(e->entries.put(e.kind()+":"+e.id(),e));final var diagnostics=new ArrayList<String>();
        for(final var runtime:snapshot.registries()) {
            final ResourceKind kind=kind(runtime.kind());if(kind==null)continue;final var id=ResourceLocation.parse(runtime.id());final var key=kind+":"+id;
            final var old=entries.get(key);if(old!=null&&!old.displayName().equals(runtime.displayName()))diagnostics.add("Static/runtime display evidence differs: "+key);
            final var source=new ResourceSource(SourceType.RUNTIME,List.of(),snapshotFile,"snapshot/"+snapshot.snapshotId()+"/"+runtime.id(),"Runtime "+snapshot.environment().sourceInstance()+" @ "+snapshot.environment().collectedAt());
            entries.put(key,new RegistryEntry(id,kind,runtime.displayName(),runtime.metadata().getOrDefault("localizationKey",""),Map.of(),List.of(source),old==null?List.of():old.textures(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,source.method()));
        }
        final var documents=new ArrayList<ResourceDocument>();
        for(final var tag:snapshot.tags()) {final var kind=kind(tag.kind());final String folder=switch(kind){case ITEM->"items";case BLOCK->"blocks";case FLUID->"fluids";};final var id=ResourceLocation.parse(tag.id());final var source=new ResourceSource(SourceType.RUNTIME,List.of(),snapshotFile,"data/"+id.namespace()+"/tags/"+folder+"/"+id.path()+".json","Resolved runtime tag @ "+snapshot.environment().collectedAt());final var json=new com.google.gson.JsonObject();json.addProperty("replace",false);json.add("values",dev.recipeeditor.companionprotocol.ProtocolCodec.GSON.toJsonTree(tag.entries()));documents.add(new ResourceDocument(source,json.toString()));}
        final var runtimeTags=new TagIndex(documents);if(!runtimeTags.diagnostics().isEmpty())throw new IllegalArgumentException("Runtime tag index is incomplete: "+runtimeTags.diagnostics());
        return new Result(new RegistryIndex(List.copyOf(entries.values())),original,runtimeTags,diagnostics);
    }
    private ResourceKind kind(final RuntimeData.Kind kind){return switch(kind){case ITEM->ResourceKind.ITEM;case BLOCK->ResourceKind.BLOCK;case FLUID->ResourceKind.FLUID;default->null;};}
}
