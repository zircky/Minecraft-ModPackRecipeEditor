package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import java.util.*;
public final class TagIndex {
    private final List<TagDefinition> definitions;
    private final Map<Key,Set<ResourceLocation>> members=new HashMap<>();
    private final Map<Key,Set<ResourceLocation>> reverse=new HashMap<>();
    private final List<ImportDiagnostic> diagnostics=new ArrayList<>();
    private record Key(ResourceKind kind,ResourceLocation id) { }
    public TagIndex(final List<ResourceDocument> documents) {
        this(documents, ResourceLayout.minecraft1201());
    }
    public TagIndex(final List<ResourceDocument> documents, final ResourceLayout layout) {
        final var parsed=new ArrayList<TagDefinition>(); final var reader=new ResourceJsonReader();
        final String kinds = layout.recipesDirectory().equals("recipe") ? "item|block|fluid" : "items|blocks|fluids";
        final var tagPath = java.util.regex.Pattern.compile("data/[^/]+/tags/(" + kinds + ")/.+\\.json");
        for (final var document:layout.usesDatapacks() ? documents : List.<ResourceDocument>of()) {
            dev.recipeeditor.core.io.SafeFiles.checkCancelled();
            final String path=document.source().internalPath();
            if (!tagPath.matcher(path).matches()) continue;
            try {
                final String[] parts=path.split("/",5);
                final var kind=switch(parts[3]) { case "item", "items"->ResourceKind.ITEM; case "block", "blocks"->ResourceKind.BLOCK; default->ResourceKind.FLUID; };
                final var id=new ResourceLocation(parts[1],parts[4].substring(0,parts[4].length()-5));
                final var json=reader.read(document.text());
                if (!json.path("values").isArray()) throw new IllegalArgumentException("Tag values must be an array");
                final var values=new ArrayList<ResourceLocation>(); final var nested=new ArrayList<ResourceLocation>();
                for (final var node:json.path("values")) {
                    final String value=node.isTextual()?node.asText():node.path("id").asText();
                    if (value.startsWith("#")) nested.add(ResourceLocation.parse(value.substring(1))); else values.add(ResourceLocation.parse(value));
                }
                parsed.add(new TagDefinition(kind,id,values,nested,json.path("replace").asBoolean(),document.source()));
            } catch (Exception error) { diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Invalid tag: "+error.getMessage())); }
        }
        definitions=List.copyOf(parsed);
        final var grouped=new HashMap<Key,List<TagDefinition>>();
        parsed.forEach(tag->grouped.computeIfAbsent(new Key(tag.kind(),tag.id()),k->new ArrayList<>()).add(tag));
        final var parents=new HashMap<Key,Set<Key>>();
        grouped.forEach((key,tags)-> {
            if (tags.size()>1 || tags.stream().anyMatch(TagDefinition::replace)) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,key.id().toString(),
                    "Tag merge/load order unresolved; membership search includes all statically possible members"));
            final var direct=new HashSet<ResourceLocation>();
            tags.forEach(tag->{ direct.addAll(tag.values()); tag.nestedTags().forEach(nested->parents.computeIfAbsent(new Key(key.kind(),nested),k->new HashSet<>()).add(key)); });
            members.put(key,direct);
        });
        final var pending=new ArrayDeque<>(members.keySet());
        final var queued=new HashSet<>(members.keySet());
        long work=0;
        boolean limited=false;
        while(!pending.isEmpty() && !limited) {
            dev.recipeeditor.core.io.SafeFiles.checkCancelled();
            final var child=pending.removeFirst(); queued.remove(child);
            for(final var parent:parents.getOrDefault(child,Set.of())) {
                boolean changed=false;
                for(final var id:List.copyOf(members.get(child))) {
                    if(++work>5000000) { limited=true; break; }
                    changed|=members.get(parent).add(id);
                }
                if(changed && queued.add(parent)) pending.addLast(parent);
                if(limited) break;
            }
        }
        if(limited) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,"Tags","Tag expansion work limit reached; membership index is incomplete"));
        members.forEach((key,values)->values.forEach(id->reverse.computeIfAbsent(new Key(key.kind(),id),k->new HashSet<>()).add(key.id())));
    }
    public Set<ResourceLocation> containing(final ResourceKind kind,final ResourceLocation id) { return Set.copyOf(reverse.getOrDefault(new Key(kind,id),Set.of())); }
    public Set<ResourceLocation> members(final ResourceKind kind,final ResourceLocation tag) { return Set.copyOf(members.getOrDefault(new Key(kind,tag),Set.of())); }
    public List<TagDefinition> definitions() { return definitions; }
    public List<ImportDiagnostic> diagnostics() { return List.copyOf(diagnostics); }
}
