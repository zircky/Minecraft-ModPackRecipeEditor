package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.util.*;

/** JavaFX-independent presentation decisions, preserving complete IDs and evidence. */
public final class ResourceBrowserViewModel {
    public enum FilterMode { NAMESPACE, SOURCE_MOD }
    public record RecipeQuery(ResourceKind kind, ResourceLocation id, boolean uses) { }
    private final RegistryIndex index;
    public ResourceBrowserViewModel(final RegistryIndex index) { this.index=Objects.requireNonNull(index); }
    public String displayName(final RegistryEntry entry,final String language) {
        return entry.localizedNames().getOrDefault(language,entry.localizedNames().getOrDefault("en_us",entry.id().toString()));
    }
    public String displayName(final MaterialDefinition material,final String language) {
        return material.localizedNames().getOrDefault(language,material.localizedNames().getOrDefault("en_us",material.id().toString()));
    }
    public String secondaryId(final RegistryEntry entry,final String language) {
        return displayName(entry,language).equals(entry.id().toString())?"":entry.id().toString();
    }
    public String tooltip(final RegistryEntry entry,final String language) {
        final String name=displayName(entry,language);
        return (name.equals(entry.id().toString())?"":name+"\n\n")+"Resource ID: "+entry.id()+"\nSource: "+source(entry)
                +"\n"+entry.confidence()+"\n"+entry.visual().status()+": "+entry.visual().explanation();
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
                .append("\nPreview: ").append(entry.visual().status()).append("\n").append(entry.visual().explanation()).append("\n\nTexture sources:\n");
        entry.visual().layers().forEach(layer->text.append(layer.source().container()).append("!").append(layer.source().internalPath()).append(" · tint: ").append(layer.argbTint()==null?"unknown/not supplied":Integer.toHexString(layer.argbTint())).append("\n"));
        text.append("\nDiscovery sources:\n");entry.sources().forEach(source->text.append(source.type()).append(" · ").append(source.modIds()).append("\n").append(source.container()).append("!").append(source.internalPath()).append("\n"));
        return text.append("\nRuntime metadata:\n").append(entry.metadata()).toString();
    }
    public Optional<RegistryEntry> form(final MaterialDefinition.Form form) {
        return index.find(form.resourceId()).stream().filter(entry->entry.kind()==form.kind()&&entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME).findFirst();
    }
}
