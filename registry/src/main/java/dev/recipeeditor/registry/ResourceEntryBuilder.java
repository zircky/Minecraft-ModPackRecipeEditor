package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import java.util.*;

final class ResourceEntryBuilder {
    List<RegistryEntry> build(final ResourceKind kind,final ResourceScanResult scan,final LocalizationService localization,
            final TagIndex tags,final String language,final List<ImportDiagnostic> diagnostics) {
        final var sources=new LinkedHashMap<ResourceLocation,List<ResourceSource>>();
        final var previews=new HashMap<ResourceLocation,ResourceVisual>();
        final var blockstates=new HashSet<ResourceLocation>();
        final var ambiguous=new HashSet<ResourceLocation>();
        final var resolver=new ModelTextureResolver(scan); final var reader=new ResourceJsonReader();
        for (final var document:scan.documents()) {
            dev.recipeeditor.core.io.SafeFiles.checkCancelled();
            final String path=document.source().internalPath();
            final String category=kind==ResourceKind.ITEM?"models/item":"(models/block|blockstates)";
            if (kind==ResourceKind.FLUID || !path.matches("assets/[^/]+/"+category+"/.+\\.json")) continue;
            try {
                if (!reader.read(document.text()).isObject()) throw new IllegalArgumentException("Model must be a JSON object");
                final String[] parts=path.split("/"); final int start=path.contains("/blockstates/")?3:4;
                final String relative=String.join("/",Arrays.copyOfRange(parts,start,parts.length));
                final var id=new ResourceLocation(parts[1],relative.substring(0,relative.length()-5));
                sources.computeIfAbsent(id,k->new ArrayList<>()).add(document.source());
                if(path.contains("/blockstates/")) { blockstates.add(id); previews.put(id,resolver.preview(document)); }
                else if(!blockstates.contains(id)) previews.put(id,resolver.preview(document));
            } catch (Exception error) { diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Invalid model resource: "+error.getMessage())); }
        }
        for (final var tag:tags.definitions()) if (tag.kind()==kind) for (final var id:tag.values())
            sources.computeIfAbsent(id,k->new ArrayList<>()).add(new ResourceSource(tag.source().type(),tag.source().modIds(),tag.source().container(),tag.source().internalPath(),"Tag value reference; not registration"));
        final var result=new ArrayList<RegistryEntry>();
        sources.forEach((id,origins)-> {
            final String key=localization.key(kind,id);
            final var paths=new HashMap<String,Set<java.nio.file.Path>>();
            origins.forEach(source->paths.computeIfAbsent(source.internalPath(),k->new HashSet<>()).add(source.container()));
            if(paths.values().stream().anyMatch(containers->containers.size()>1)) ambiguous.add(id);
            final var visual=ambiguous.contains(id)?ResourceVisual.unavailable(ResourceVisual.Status.AMBIGUOUS_SOURCE,"Multiple definitions; resource pack precedence is unknown")
                    :previews.getOrDefault(id,ResourceVisual.unavailable(ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED,"Tag evidence has no visual metadata; runtime registration and texture references required"));
            final var images=visual.layers().stream().map(ResourceVisual.Layer::source).toList();
            result.add(new RegistryEntry(id,kind,localization.displayName(id,key,language),key,localization.translations(key),origins,
                    images,DiscoveryConfidence.INFERRED_FROM_RESOURCES,
                    kind==ResourceKind.FLUID?"Explicit fluid tag reference; registration unknown":"Model / blockstate / tag evidence; registration unknown",visual,Map.of()));
        });
        result.sort(Comparator.comparing(RegistryEntry::id)); return List.copyOf(result);
    }
}
