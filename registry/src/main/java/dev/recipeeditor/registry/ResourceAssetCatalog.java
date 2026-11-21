package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

/** Never invents pack priority from filesystem/JAR enumeration order. */
public final class ResourceAssetCatalog {
    private final Map<String,List<ResourceSource>> textures=new HashMap<>();
    private final Map<ResourceLocation,List<ResourceDocument>> models=new HashMap<>();
    private final Map<ResourceLocation,List<ResourceLocation>> sprites=new HashMap<>();
    public ResourceAssetCatalog(final ResourceScanResult scan) {
        scan.textures().forEach(s->textures.computeIfAbsent(s.internalPath(),ignored->new ArrayList<>()).add(s));
        for (final var document:scan.documents()) {
            final String path=document.source().internalPath();
            if (path.matches("assets/[^/]+/models/.+\\.json")) {
                final String[] parts=path.split("/",4);
                models.computeIfAbsent(new ResourceLocation(parts[1],parts[3].substring(0,parts[3].length()-5)),ignored->new ArrayList<>()).add(document);
            }
            if (path.matches("assets/[^/]+/atlases/.+\\.json")) atlas(document);
        }
    }
    private void atlas(final ResourceDocument document) {
        try {
            for (final var source:new ResourceJsonReader().read(document.text()).path("sources")) {
                if(Set.of("directory","minecraft:directory").contains(source.path("type").asText())) {
                    final String directory="textures/"+source.path("source").asText()+"/",prefix=source.path("prefix").asText();
                    for(final var candidates:textures.values())for(final var texture:candidates) {
                        final String[] parts=texture.internalPath().split("/",3);if(parts.length!=3||!parts[2].startsWith(directory))continue;
                        final String relative=parts[2].substring(directory.length(),parts[2].length()-4);
                        sprites.computeIfAbsent(new ResourceLocation(parts[1],prefix+relative),ignored->new ArrayList<>()).add(new ResourceLocation(parts[1],parts[2].substring("textures/".length(),parts[2].length()-4)));
                    }
                    continue;
                }
                if (!Set.of("single","minecraft:single").contains(source.path("type").asText())) continue;
                final var resource=ResourceLocation.parse(source.path("resource").asText());
                final var sprite=ResourceLocation.parse(source.path("sprite").asText(resource.toString()));
                sprites.computeIfAbsent(sprite,ignored->new ArrayList<>()).add(resource);
            }
        } catch (Exception ignored) { /* Invalid metadata supplies no reliable mapping. */ }
    }
    List<ResourceDocument> models(final ResourceLocation id) { return models.getOrDefault(id,List.of()); }
    public List<ResourceSource> texture(final String reference) {
        try {
            var id=ResourceLocation.parse(reference);
            final var mapped=sprites.getOrDefault(id,List.of()).stream().distinct().toList();
            if (mapped.size()>1) return List.of();
            if (mapped.size()==1) id=mapped.getFirst();
            return List.copyOf(textures.getOrDefault("assets/"+id.namespace()+"/textures/"+id.path()+".png",List.of()));
        } catch (IllegalArgumentException error) { return List.of(); }
    }
}
