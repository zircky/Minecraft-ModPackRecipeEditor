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
        final var resolver=new RuntimeResourceResolver(original.assets());
        for(final var runtime:snapshot.registries()) {
            final ResourceKind kind=kind(runtime.kind());if(kind==null)continue;final var id=ResourceLocation.parse(runtime.id());final var key=kind+":"+id;
            final var old=entries.get(key);if(old!=null&&!old.displayName().equals(runtime.displayName()))diagnostics.add("Static/runtime display evidence differs: "+key);
            final var source=new ResourceSource(SourceType.RUNTIME,List.of(),snapshotFile,"snapshot/"+snapshot.snapshotId()+"/"+runtime.id(),"Runtime "+snapshot.environment().sourceInstance()+" @ "+snapshot.environment().collectedAt());
            final String translationKey=runtime.metadata().getOrDefault("localizationKey",old==null?"":old.localizationKey());
            final var names=new HashMap<String,String>();
            if(old!=null&&(translationKey.isBlank()||old.localizationKey().equals(translationKey)))names.putAll(old.localizedNames());
            names.putAll(resolver.localization().translations(translationKey));
            names.putAll(resolver.localization().componentTranslations(runtime.metadata().get("nameComponent")));
            for(final String language:List.of("en_us","ru_ru")) {
                final String name=runtime.metadata().get("name."+language);
                if(!id.toString().equals(name)&&resolver.localization().resolvedRuntimeName(name,runtime.metadata().get("nameComponent")))names.put(language,name);
            }
            final var visual=resolver.visual(kind,id,runtime.metadata(),old);
            entries.put(key,new RegistryEntry(id,kind,runtime.displayName(),translationKey,names,List.of(source),visual.layers().stream().map(ResourceVisual.Layer::source).toList(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,source.method(),visual,runtime.metadata()));
        }
        final var documents=new ArrayList<ResourceDocument>();
        for(final var tag:snapshot.tags()) {final var kind=kind(tag.kind());final String folder=switch(kind){case ITEM->"items";case BLOCK->"blocks";case FLUID->"fluids";};final var id=ResourceLocation.parse(tag.id());final var source=new ResourceSource(SourceType.RUNTIME,List.of(),snapshotFile,"data/"+id.namespace()+"/tags/"+folder+"/"+id.path()+".json","Resolved runtime tag @ "+snapshot.environment().collectedAt());final var json=new com.google.gson.JsonObject();json.addProperty("replace",false);json.add("values",dev.recipeeditor.companionprotocol.ProtocolCodec.GSON.toJsonTree(tag.entries()));documents.add(new ResourceDocument(source,json.toString()));}
        final var runtimeTags=new TagIndex(documents);if(!runtimeTags.diagnostics().isEmpty())throw new IllegalArgumentException("Runtime tag index is incomplete: "+runtimeTags.diagnostics());
        // Material names use Minecraft's shared Language on the integrated server. Attribute a locale only
        // when the same client capture explicitly reports one consistent language; never guess on a server.
        final var nameLanguages=snapshot.registries().stream().map(row->row.metadata().get("runtimeNameLanguage")).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        final var materials=snapshot.materials().stream().map(value->{
            final var names=new HashMap<>(resolver.localization().translations(value.localizationKey()));names.putAll(value.localizedNames());
            final var metadata=new HashMap<>(value.metadata());
            if(nameLanguages.size()==1) {
                final String language=nameLanguages.iterator().next();
                if(Set.of("en_us","ru_ru").contains(language)&&resolver.localization().resolvedRuntimeName(value.name(),value.metadata().get("nameComponent"))) {
                    names.put(language,value.name());metadata.put("runtimeNameLanguage",language);
                }
            }
            return new MaterialDefinition(ResourceLocation.parse(value.id()),value.name(),value.localizationKey(),names,value.properties(),value.flags(),
                    value.forms().stream().map(form->new MaterialDefinition.Form(form.prefix(),Objects.requireNonNull(kind(form.kind())),ResourceLocation.parse(form.resourceId()))).toList(),metadata);
        }).toList();
        return new Result(new RegistryIndex(List.copyOf(entries.values()),original.assets(),materials),original,runtimeTags,diagnostics);
    }
    private ResourceKind kind(final RuntimeData.Kind kind){return switch(kind){case ITEM->ResourceKind.ITEM;case BLOCK->ResourceKind.BLOCK;case FLUID->ResourceKind.FLUID;default->null;};}
}
