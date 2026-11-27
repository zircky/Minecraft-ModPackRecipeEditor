package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.util.*;

/** JavaFX-independent presentation decisions, preserving complete IDs and evidence. */
public final class ResourceBrowserViewModel {
    public enum FilterMode { NAMESPACE, SOURCE_MOD }
    public enum Evidence { ALL, RUNTIME, STATIC, MISSING_TEXTURE, UNSUPPORTED_RENDERER }
    public record RecipeQuery(ResourceKind kind, ResourceLocation id, boolean uses) { }
    private final RegistryIndex index;
    private UnifiedResourceIndex unified;
    public ResourceBrowserViewModel(final RegistryIndex index) { this.index=Objects.requireNonNull(index); }
    private UnifiedResourceIndex unified(){if(unified==null)unified=new UnifiedResourceIndex(index);return unified;}
    public List<RegistryEntry> unifiedSearch(final UnifiedResourceIndex.Type type,final String query,final FilterMode mode,final String selected,final Evidence evidence) {
        final String text=query.strip().toLowerCase(Locale.ROOT);
        return unified().entries(type).stream().filter(e->text.isEmpty()||e.id().toString().contains(text)||e.localizedNames().values().stream().anyMatch(n->n.toLowerCase(Locale.ROOT).contains(text)))
                .filter(e->selected.isEmpty()||(mode==FilterMode.NAMESPACE?e.id().namespace().equals(selected):e.sources().stream().anyMatch(origin->origin.modIds().contains(selected))))
                .filter(e->matches(e,evidence)).toList();
    }
    public boolean matches(final RegistryEntry entry,final Evidence evidence){return switch(evidence){
        case ALL->true;case RUNTIME->entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME;
        case STATIC->entry.confidence()!=DiscoveryConfidence.CONFIRMED_BY_RUNTIME;
        case MISSING_TEXTURE->entry.visual().status()==ResourceVisual.Status.TEXTURE_NOT_FOUND||entry.visual().status()==ResourceVisual.Status.INVALID_TEXTURE;
        case UNSUPPORTED_RENDERER->entry.visual().status()==ResourceVisual.Status.RENDERING_UNSUPPORTED||entry.visual().status()==ResourceVisual.Status.UNSUPPORTED_RENDERER;
    };}
    public List<String> unifiedFilterValues(final FilterMode mode){return java.util.stream.Stream.concat(index.entries(ResourceKind.ITEM).stream(),index.entries(ResourceKind.BLOCK).stream()).flatMap(e->mode==FilterMode.NAMESPACE?java.util.stream.Stream.of(e.id().namespace()):e.sources().stream().flatMap(s->s.modIds().stream())).distinct().sorted().toList();}
    public String displayName(final RegistryEntry entry,final String language) {
        return entry.localizedNames().getOrDefault(language,entry.localizedNames().getOrDefault("en_us",
                entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME&&entry.localizedNames().size()==1
                        ?entry.localizedNames().values().iterator().next():entry.id().toString()));
    }
    public String displayName(final MaterialDefinition material,final String language) {
        return material.localizedNames().getOrDefault(language,material.localizedNames().getOrDefault("en_us",
                material.localizedNames().size()==1?material.localizedNames().values().iterator().next():material.id().toString()));
    }
    public String secondaryId(final RegistryEntry entry,final String language) {
        return displayName(entry,language).equals(entry.id().toString())?"":entry.id().toString();
    }
    public String tooltip(final RegistryEntry entry,final String language) {
        final String name=displayName(entry,language);
        return (name.equals(entry.id().toString())?"":name+"\n\n")+"Resource ID: "+entry.id()+"\nSource: "+source(entry)
                +"\n"+entry.confidence()+"\nIndexed texture evidence: "+entry.visual().status()+": "+entry.visual().explanation()+"\nInventory preview source is shown on the image and in preview details";
    }
    public String source(final RegistryEntry entry) {
        final var mods=entry.sources().stream().flatMap(s->s.modIds().stream()).distinct().sorted().toList();
        return mods.isEmpty()?"Namespace: "+entry.id().namespace():"Archive mods: "+String.join(", ",mods);
    }
    public List<String> filterValues(final ResourceKind kind,final FilterMode mode) {
        return (mode==FilterMode.NAMESPACE?index.entries(kind).stream().map(e->e.id().namespace()):index.entries(kind).stream().flatMap(e->e.sources().stream()).flatMap(s->s.modIds().stream())).distinct().sorted().toList();
    }
    public List<RegistryEntry> search(final ResourceKind kind,final String query,final FilterMode mode,final String selected) {
        return new RegistrySearchService(index).search(kind,query,mode==FilterMode.SOURCE_MOD?selected:"").stream().filter(e->mode!=FilterMode.NAMESPACE||selected.isEmpty()||e.id().namespace().equals(selected)).toList();
    }
    public String details(final RegistryEntry entry,final String language) {
        final var mods=entry.sources().stream().flatMap(s->s.modIds().stream()).distinct().toList();
        final var text=new StringBuilder(displayName(entry,language)).append("\nID: ").append(entry.id()).append("\nType: ").append(entry.kind())
                .append("\nNamespace: ").append(entry.id().namespace()).append("\nSource Mod: ")
                .append(mods.isEmpty()?"UNKNOWN":String.join(", ",mods)).append(" (physical archive metadata; runtime owner may be unknown)")
                .append("\nConfidence: ").append(entry.confidence()).append("\nRuntime confirmed: ").append(entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME)
                .append("\nLocalization key: ").append(entry.localizationKey()).append("\nTranslations: ").append(entry.localizedNames())
                .append("\nIndexed texture evidence: ").append(entry.visual().status()).append("\n").append(entry.visual().explanation()).append("\n\nTexture sources:\n");
        entry.visual().layers().forEach(layer->text.append(layer.source().container()).append("!").append(layer.source().internalPath()).append(" · tint: ").append(layer.argbTint()==null?"unknown/not supplied":Integer.toHexString(layer.argbTint())).append("\n"));
        text.append("\nDiscovery sources:\n");entry.sources().forEach(source->text.append(source.type()).append(" · ").append(source.modIds()).append("\n").append(source.container()).append("!").append(source.internalPath()).append("\n"));
        if(entry.kind()==ResourceKind.ITEM)text.append("\nItem ID: ").append(entry.id()).append("\nBlock ID: ").append(unified().relationships().block(entry.id()).map(Object::toString).orElse("UNKNOWN"));
        if(entry.kind()==ResourceKind.BLOCK){
            text.append("\nBlock ID: ").append(entry.id()).append("\nItem IDs: ").append(unified().relationships().items(entry.id()).isEmpty()?(unified().relationships().hasNoItem(entry.id())?"NONE (runtime confirmed)":"UNKNOWN"):unified().relationships().items(entry.id()));
            text.append("\nBlockstate definitions:\n");index.assetCatalog().blockstates(entry.id()).forEach(document->text.append(document.source().container()).append("!").append(document.source().internalPath()).append("\n").append(document.text().substring(0,Math.min(4096,document.text().length()))).append("\n"));
        }
        if(entry.kind()==ResourceKind.ITEM){
            text.append("\nItem model definitions (asset evidence):\n");index.assetCatalog().modelDefinitions(new ResourceLocation(entry.id().namespace(),"item/"+entry.id().path())).forEach(document->text.append(document.source().container()).append("!").append(document.source().internalPath()).append("\n"));
        }
        return text.append("\nRuntime metadata:\n").append(entry.metadata()).toString();
    }
    public Optional<RegistryEntry> form(final MaterialDefinition.Form form) {
        return index.find(form.resourceId()).stream().filter(entry->entry.kind()==form.kind()&&entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME).findFirst();
    }
}
