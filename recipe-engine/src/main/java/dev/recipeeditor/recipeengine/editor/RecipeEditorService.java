package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Per-project overlay and command context. Imported definitions are never mutated. */
public final class RecipeEditorService {
    private final RecipeIndex originals;
    private RecipeIndex authoringBases;
    private RecipeIndex visibleBases;
    private Set<ResourceLocation> incompatible=Set.of();
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
        authoringBases=originals;visibleBases=originals;
    }
    /** Reuses source conflict quarantine without changing the persistent project change records. */
    public synchronized void applyRuntimeEvidence(final dev.recipeeditor.registry.RegistryIndex registry,final dev.recipeeditor.registry.TagIndex tags,final Set<ResourceLocation> incompatible,final Set<ResourceLocation> runtimeIds,final String environment) {
        effective.updateTags(tags);
        reconcileRuntime(registry,incompatible,runtimeIds,environment);
    }

    /** The display projection may contain inspection-only definitions. Only verified bases enter authoring. */
    public synchronized void applyRuntimeCatalog(final dev.recipeeditor.registry.RegistryIndex registry, final RecipeIndex visible,
            final RecipeIndex authorable, final Set<ResourceLocation> incompatible, final Set<ResourceLocation> runtimeIds, final String environment) {
        authoringBases=authorable;
        visibleBases=visible;
        effective.replaceBase(visible);
        reconcileRuntime(registry,incompatible,runtimeIds,environment);
    }

    private void reconcileRuntime(final dev.recipeeditor.registry.RegistryIndex registry, final Set<ResourceLocation> incompatible,
            final Set<ResourceLocation> runtimeIds, final String environment) {
        this.incompatible=Set.copyOf(incompatible);
        this.runtimeIds=Set.copyOf(runtimeIds);validator.updateRegistry(registry);final var affected=new ArrayList<ResourceLocation>();
        final var blockedIds=new HashSet<ResourceLocation>();
        for(final var change:changes.values()) {
            final boolean blocked=change.type()==RecipeChangeType.CREATE?runtimeIds.contains(change.id()):!matchesBase(change);
            if(blocked) {
                blockedIds.add(change.id());
                final String peer=change.metadata().get(RecipeMigrationLink.PEER);
                if(peer!=null&&RecipeMigrationLink.valid(change,changes))blockedIds.add(ResourceLocation.parse(peer));
            }
        }
        for(final var id:blockedIds) {
            final var change=changes.remove(id);
            if(change==null)continue;
            effective.apply(id,null);
            conflicts.put(id,new SourceConflict(id,"Runtime evidence conflicts with project base in "+environment+". Original references are retained.",change,references(id)));
            affected.add(id);
        }
        for(final var conflict:List.copyOf(conflicts.values())) {
            final var change=conflict.savedChange();
            if(runtimeChange(change) && matchesBase(change)) {
                final String linked=change.metadata().get(RecipeMigrationLink.PEER);
                if(linked!=null) {
                    final var peer=conflicts.get(ResourceLocation.parse(linked));
                    if(peer==null||!matchesBase(peer.savedChange())||!RecipeMigrationLink.valid(change,Map.of(peer.recipeId(),peer.savedChange())))continue;
                    conflicts.remove(peer.recipeId());
                    applyWithoutNotification(peer.recipeId(),peer.savedChange());
                    affected.add(peer.recipeId());
                }
                conflicts.remove(change.id());
                applyWithoutNotification(change.id(),change);
                affected.add(change.id());
            }
        }
        if(!affected.isEmpty())history.clear();affected.forEach(this::notifyChanged);
    }
    public RecipeValidator validator() { return validator; }
    public RecipeIndex originals() { return originals; }
    public synchronized RecipeIndex authoringBases() { return authoringBases; }
    public synchronized boolean canChange(final ResourceLocation id) {
        if(conflicts.containsKey(id)||effective.findById(id).isEmpty())return false;
        final var existing=changes.get(id);
        if(existing!=null&&existing.metadata().containsKey(RecipeMigrationLink.PEER))return false;
        return existing!=null&&existing.type()==RecipeChangeType.CREATE
                || !incompatible.contains(id)&&!authoringBases.findById(id).isEmpty();
    }
    public synchronized boolean canReset(final ResourceLocation id) {
        final var existing=changes.get(id);
        return conflicts.containsKey(id) || existing!=null&&!existing.metadata().containsKey(RecipeMigrationLink.PEER);
    }
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
        final var current=effective.findById(id); return current.isEmpty()?visibleBases.findById(id):current;
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
        requireUnlinked(id);
        if(!canChange(id))throw new IllegalArgumentException("No verified active base for this recipe");
        if(!id.equals(definition.id())) throw new IllegalArgumentException("Editing cannot rename an ID; use Duplicate");
        if(effective.findById(id).isEmpty()) throw new IllegalArgumentException("Recipe is deleted or missing");
        final var valid=validated(definition); final var old=changes.get(id);
        final var type=old!=null&&old.type()==RecipeChangeType.CREATE?RecipeChangeType.CREATE:RecipeChangeType.UPDATE;
        final var bases=type==RecipeChangeType.CREATE?List.<OriginalRecipeReference>of():references(id);
        final var baseline=authoringBases.findById(id);
        if(type==RecipeChangeType.UPDATE && baseline.size()==1 && new RecipeDiffService().compare(baseline.getFirst().rawJson(),valid.rawJson()).isEmpty()) { reset(id); return; }
        if(type==RecipeChangeType.UPDATE && baseline.size()==1 && new RuntimeRecipeBase().matches(baseline.getFirst().source())
                && !baseline.getFirst().type().equals(valid.type()))
            throw new IllegalArgumentException("Changing this runtime serializer requires a verified recipe migration");
        change(id,new RecipeChange(id,type,bases,List.of(valid),Instant.now(),runtimeMetadata(id)));
    }
    private RecipeDefinition validated(final RecipeDefinition recipe) {
        final var validation=validator.validate(recipe.id(),recipe.rawJson(),recipe.source());
        if(!validation.valid()) throw new IllegalArgumentException(validation.issues().toString()); return validation.recipe();
    }
    public synchronized void delete(final ResourceLocation id) {
        requireNoConflict(id);
        requireUnlinked(id);
        if(!canChange(id))throw new IllegalArgumentException("No verified active base for this recipe");
        if(effective.findById(id).isEmpty()) throw new IllegalArgumentException("Recipe is already deleted or missing");
        final var old=changes.get(id);
        if(old!=null && old.type()==RecipeChangeType.CREATE) change(id,null);
        else change(id,new RecipeChange(id,RecipeChangeType.DELETE,references(id),List.of(),Instant.now(),runtimeMetadata(id)));
    }
    public synchronized RecipeDefinition duplicate(final RecipeDefinition source,final ResourceLocation target) {
        if(hasConflict(source.id()) || effective.findById(source.id()).stream().noneMatch(r->r.equals(source))) throw new IllegalArgumentException("Source is not an active effective definition");
        final var clone=new RecipeDefinition(target,source.type(),source.inputs(),source.outputs(),source.layout(),source.properties(),source.source(),source.rawJson(),source.status(),source.diagnostics());
        create(clone); return effective.findById(target).getFirst();
    }
    public synchronized void reset(final ResourceLocation id) { requireNoConflict(id);requireUnlinked(id);if(changes.containsKey(id)) change(id,null); }

    public synchronized void migrate(final ResourceLocation originalId,final RecipeDefinition target) {
        requireNoConflict(originalId);
        final var targetId=target.id();
        if(originalId.equals(targetId))throw new IllegalArgumentException("This migration requires an explicit new ID");
        if(!canChange(originalId)||authoringBases.findById(originalId).size()!=1 || changes.containsKey(originalId))
            throw new IllegalArgumentException("Machine replacement needs one unchanged original project definition");
        if(runtimeIds.contains(targetId)||!originals.findById(targetId).isEmpty()||changes.containsKey(targetId)||conflicts.containsKey(targetId))
            throw new IllegalArgumentException("Target recipe ID already exists");
        final var valid=validated(target);
        final var metadata=new LinkedHashMap<>(runtimeMetadata(originalId));
        metadata.put(RecipeMigrationLink.PEER,targetId.toString());
        final var removed=new RecipeChange(originalId,RecipeChangeType.DELETE,references(originalId),List.of(),Instant.now(),metadata);
        final var added=new RecipeChange(targetId,RecipeChangeType.CREATE,List.of(),List.of(valid),
                Instant.now(),Map.of(RecipeMigrationLink.PEER,originalId.toString()));
        history.execute(new RecipeCommand() {
            @Override public void execute() { applyPair(originalId,removed,targetId,added); }
            @Override public void undo() { applyPair(originalId,null,targetId,null); }
            @Override public long retainedBytes() { return 2L*valid.rawJson().length(); }
        });
    }

    private void requireUnlinked(final ResourceLocation id) {
        final var existing=changes.get(id);
        if(existing!=null&&existing.metadata().containsKey(RecipeMigrationLink.PEER))
            throw new IllegalStateException("A linked replacement must stay intact. Undo the migration before editing either entry.");
    }

    private void applyPair(final ResourceLocation first,final RecipeChange before,final ResourceLocation second,
            final RecipeChange after) {
        applyWithoutNotification(first,before);
        applyWithoutNotification(second,after);
        notifyChanged(first);
        notifyChanged(second);
    }
    public synchronized void discardConflict(final ResourceLocation id) { conflicts.remove(id); notifyChanged(id); }
    private List<OriginalRecipeReference> references(final ResourceLocation id) { return authoringBases.findById(id).stream().map(OriginalRecipeReference::from).toList(); }
    private Map<String,String> runtimeMetadata(final ResourceLocation id) {
        final var bases=authoringBases.findById(id);
        if(bases.size()!=1||!new RuntimeRecipeBase().matches(bases.getFirst().source()))return Map.of();
        final var recipe=bases.getFirst();
        return Map.of("mre.runtime.originalSerializerJson",recipe.rawJson());
    }
    private boolean runtimeChange(final RecipeChange change) {
        return change.originals().stream().anyMatch(reference->new RuntimeRecipeBase().matches(reference.source()));
    }
    private boolean matchesBase(final RecipeChange change) {
        if(change.type()==RecipeChangeType.CREATE)return references(change.id()).isEmpty()&&!runtimeIds.contains(change.id());
        return !incompatible.contains(change.id())&&new HashSet<>(references(change.id())).equals(new HashSet<>(change.originals()));
    }
    private void requireNoConflict(final ResourceLocation id) { if(conflicts.containsKey(id)) throw new IllegalStateException("Saved change is blocked by a source conflict; review/discard it first"); }
    private void change(final ResourceLocation id,final RecipeChange replacement) {
        final var before=changes.get(id);
        history.execute(new RecipeCommand() {
            @Override public void execute() { apply(id,replacement); }
            @Override public void undo() { apply(id,before); }
            @Override public long retainedBytes() { return bytes(before)+bytes(replacement); }
            private long bytes(final RecipeChange value) { return value==null?0:value.replacement().stream().mapToLong(r->2L*r.rawJson().length()).sum()
                    + value.metadata().values().stream().mapToLong(text->2L*text.length()).sum(); }
        });
    }
    private void apply(final ResourceLocation id,final RecipeChange change) {
        applyWithoutNotification(id,change);
        notifyChanged(id);
    }
    private void applyWithoutNotification(final ResourceLocation id,final RecipeChange change) {
        effective.apply(id,change);
        if(change==null) changes.remove(id); else changes.put(id,change);
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
            final boolean matching=matchesBase(change);
            if(!matching) blocked.put(change.id(),new SourceConflict(change.id(),"Original source/content changed or ID now collides; automatic application blocked",change,current));
            else accepted.put(change.id(),change);
        }
        for(final var change:List.copyOf(accepted.values()))if(!RecipeMigrationLink.valid(change,accepted)) {
            accepted.remove(change.id());
            blocked.put(change.id(),new SourceConflict(change.id(),"The linked recipe replacement is missing or conflicted",change,references(change.id())));
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
