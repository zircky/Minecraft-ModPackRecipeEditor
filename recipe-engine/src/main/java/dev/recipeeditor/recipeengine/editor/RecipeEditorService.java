package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Per-project overlay and command context. Imported definitions are never mutated. */
public final class RecipeEditorService {
    private final RecipeIndex originals;
    private Set<ResourceLocation> runtimeIds=Set.of();
    private final RecipeValidator validator;
    private final EffectiveRecipeIndex effective;
    private final Map<ResourceLocation,RecipeChange> changes=new LinkedHashMap<>();
    private final Map<ResourceLocation,SourceConflict> conflicts=new LinkedHashMap<>();
    private final CommandHistory history=new CommandHistory();
    private final List<Consumer<ResourceLocation>> listeners=new ArrayList<>();
    private RecipeChangeSet persisted=new RecipeChangeSet();
    public RecipeEditorService(final RecipeIndex originals,final RecipeValidator validator) {
        this.originals=Objects.requireNonNull(originals); this.validator=Objects.requireNonNull(validator); effective=new EffectiveRecipeIndex(originals);
    }
    /** Reuses source conflict quarantine without changing the persistent project change records. */
    public synchronized void applyRuntimeEvidence(final dev.recipeeditor.registry.RegistryIndex registry,final dev.recipeeditor.registry.TagIndex tags,final Set<ResourceLocation> incompatible,final Set<ResourceLocation> runtimeIds,final String environment) {
        this.runtimeIds=Set.copyOf(runtimeIds);validator.updateRegistry(registry);effective.updateTags(tags);final var affected=new ArrayList<ResourceLocation>();
        for(final var change:List.copyOf(changes.values())) {
            final boolean blocked=change.type()==RecipeChangeType.CREATE?runtimeIds.contains(change.id()):incompatible.contains(change.id());
            if(!blocked)continue;changes.remove(change.id());effective.apply(change.id(),null);conflicts.put(change.id(),new SourceConflict(change.id(),"Runtime evidence conflicts with project base in "+environment+". Static original references are retained; review before applying/exporting.",change,references(change.id())));affected.add(change.id());
        }
        if(!affected.isEmpty())history.clear();affected.forEach(this::notifyChanged);
    }
    public RecipeValidator validator() { return validator; }
    public RecipeIndex originals() { return originals; }
    public EffectiveRecipeIndex effective() { return effective; }
    public synchronized RecipeChangeSet snapshot() { return new RecipeChangeSet(changes); }
    public synchronized List<SourceConflict> conflicts() { return List.copyOf(conflicts.values()); }
    public synchronized boolean hasConflict(final ResourceLocation id) { return conflicts.containsKey(id); }
    public synchronized void addListener(final Consumer<ResourceLocation> listener) { listeners.add(listener); }
    public synchronized void removeListener(final Consumer<ResourceLocation> listener) { listeners.remove(listener); }
    public synchronized RecipeModificationStatus status(final ResourceLocation id) {
        final var change=changes.get(id); if(change==null) return RecipeModificationStatus.UNCHANGED;
        return switch(change.type()) { case CREATE->RecipeModificationStatus.CREATED; case UPDATE->RecipeModificationStatus.MODIFIED; case DELETE->RecipeModificationStatus.DELETED; };
    }
    public synchronized List<RecipeDefinition> displayed(final ResourceLocation id) {
        final var current=effective.findById(id); return current.isEmpty()?originals.findById(id):current;
    }
    public synchronized boolean dirty() { return !persistentSnapshot().equals(persisted); }
    public synchronized RecipeChangeSet persistedSnapshot() { return persisted; }
    public synchronized void markPersisted(final RecipeChangeSet saved) { persisted=Objects.requireNonNull(saved); }
    public synchronized void create(final RecipeDefinition definition) {
        if(runtimeIds.contains(definition.id()) || !originals.findById(definition.id()).isEmpty() || changes.containsKey(definition.id()) || conflicts.containsKey(definition.id())) throw new IllegalArgumentException("Recipe ID already exists; use Edit or Reset");
        final var valid=validated(definition);
        change(valid.id(),new RecipeChange(valid.id(),RecipeChangeType.CREATE,List.of(),List.of(valid),Instant.now(),Map.of()));
    }
    public synchronized void update(final ResourceLocation id,final RecipeDefinition definition) {
        requireNoConflict(id);
        if(!id.equals(definition.id())) throw new IllegalArgumentException("Editing cannot rename an ID; use Duplicate");
        if(effective.findById(id).isEmpty()) throw new IllegalArgumentException("Recipe is deleted or missing");
        final var valid=validated(definition); final var old=changes.get(id);
        final var type=old!=null&&old.type()==RecipeChangeType.CREATE?RecipeChangeType.CREATE:RecipeChangeType.UPDATE;
        final var bases=type==RecipeChangeType.CREATE?List.<OriginalRecipeReference>of():references(id);
        if(type==RecipeChangeType.UPDATE && originals.findById(id).size()==1 && new RecipeDiffService().compare(originals.findById(id).getFirst().rawJson(),valid.rawJson()).isEmpty()) { reset(id); return; }
        change(id,new RecipeChange(id,type,bases,List.of(valid),Instant.now(),Map.of()));
    }
    private RecipeDefinition validated(final RecipeDefinition recipe) {
        final var validation=validator.validate(recipe.id(),recipe.rawJson(),recipe.source());
        if(!validation.valid()) throw new IllegalArgumentException(validation.issues().toString()); return validation.recipe();
    }
    public synchronized void delete(final ResourceLocation id) {
        requireNoConflict(id);
        if(effective.findById(id).isEmpty()) throw new IllegalArgumentException("Recipe is already deleted or missing");
        final var old=changes.get(id);
        if(old!=null && old.type()==RecipeChangeType.CREATE) change(id,null);
        else change(id,new RecipeChange(id,RecipeChangeType.DELETE,references(id),List.of(),Instant.now(),Map.of()));
    }
    public synchronized RecipeDefinition duplicate(final RecipeDefinition source,final ResourceLocation target) {
        if(hasConflict(source.id()) || effective.findById(source.id()).stream().noneMatch(r->r.equals(source))) throw new IllegalArgumentException("Source is not an active effective definition");
        final var clone=new RecipeDefinition(target,source.type(),source.inputs(),source.outputs(),source.layout(),source.properties(),source.source(),source.rawJson(),source.status(),source.diagnostics());
        create(clone); return effective.findById(target).getFirst();
    }
    public synchronized void reset(final ResourceLocation id) { requireNoConflict(id); if(changes.containsKey(id)) change(id,null); }
    public synchronized void discardConflict(final ResourceLocation id) { conflicts.remove(id); notifyChanged(id); }
    private List<OriginalRecipeReference> references(final ResourceLocation id) { return originals.findById(id).stream().map(OriginalRecipeReference::from).toList(); }
    private void requireNoConflict(final ResourceLocation id) { if(conflicts.containsKey(id)) throw new IllegalStateException("Saved change is blocked by a source conflict; review/discard it first"); }
    private void change(final ResourceLocation id,final RecipeChange replacement) {
        final var before=changes.get(id);
        history.execute(new RecipeCommand() {
            @Override public void execute() { apply(id,replacement); }
            @Override public void undo() { apply(id,before); }
            @Override public long retainedBytes() { return bytes(before)+bytes(replacement); }
            private long bytes(final RecipeChange value) { return value==null?0:value.replacement().stream().mapToLong(r->2L*r.rawJson().length()).sum(); }
        });
    }
    private void apply(final ResourceLocation id,final RecipeChange change) {
        effective.apply(id,change);
        if(change==null) changes.remove(id); else changes.put(id,change);
        notifyChanged(id);
    }
    private void notifyChanged(final ResourceLocation id) { for(final var listener:List.copyOf(listeners)) listener.accept(id); }
    public synchronized boolean canUndo() { return history.canUndo(); }
    public synchronized boolean canRedo() { return history.canRedo(); }
    public synchronized void undo() { history.undo(); }
    public synchronized void redo() { history.redo(); }
    /** Load validates every change first; incompatible bases are quarantined, never silently applied. */
    public synchronized void restore(final RecipeChangeSet saved) {
        final var accepted=new LinkedHashMap<ResourceLocation,RecipeChange>(); final var blocked=new LinkedHashMap<ResourceLocation,SourceConflict>();
        for(final var change:saved.changes().values()) {
            if(change.type()!=RecipeChangeType.DELETE) validated(change.replacement().getFirst());
            final var current=references(change.id());
            final boolean matching=change.type()==RecipeChangeType.CREATE?current.isEmpty():new HashSet<>(current).equals(new HashSet<>(change.originals()));
            if(!matching) blocked.put(change.id(),new SourceConflict(change.id(),"Original source/content changed or ID now collides; automatic application blocked",change,current));
            else accepted.put(change.id(),change);
        }
        final var affected=new HashSet<>(changes.keySet()); affected.addAll(accepted.keySet());
        changes.keySet().stream().toList().forEach(id->effective.apply(id,null)); changes.clear(); conflicts.clear(); conflicts.putAll(blocked);
        accepted.forEach((id,change)->{ changes.put(id,change); effective.apply(id,change); });
        persisted=saved; history.clear(); affected.forEach(this::notifyChanged);
    }
    public synchronized RecipeChangeSet persistentSnapshot() {
        final var all=new LinkedHashMap<>(changes); conflicts.forEach((id,conflict)->all.put(id,conflict.savedChange())); return new RecipeChangeSet(all);
    }
}
