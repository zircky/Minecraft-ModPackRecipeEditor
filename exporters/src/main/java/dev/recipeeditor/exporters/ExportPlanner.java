package dev.recipeeditor.exporters;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Planning and generation are pure with respect to the filesystem: only bounded reads are performed. */
public final class ExportPlanner {
    private final List<RecipeExporter> exporters;
    private final ExportValidator validator;
    public ExportPlanner(final List<RecipeExporter> exporters,final ExportValidator validator){this.exporters=List.copyOf(exporters);this.validator=validator;if(exporters.stream().map(RecipeExporter::format).distinct().count()!=exporters.size())throw new IllegalArgumentException("Duplicate exporter format");}
    public ExportPlan plan(final ExportRequest request) {
        final var context=request.context();final var diagnostics=new ArrayList<ExportDiagnostic>();final var planned=new ArrayList<PlannedRecipe>();final var files=new TreeMap<String,String>();
        final var changes=new TreeMap<ResourceLocation,RecipeChange>();
        for(final var id:request.selectedIds()) {final var change=context.savedChanges().changes().get(id);if(change==null)diagnostics.add(error(ExportDiagnostic.Code.EXPORT_INVALID_RECIPE,"Selected ID has no saved change: "+id,context));else changes.put(id,change);}
        final var copies=new HashSet<ResourceLocation>();
        for(final var copy:request.unchangedCopies()) {
            if(changes.containsKey(copy.id())||context.savedChanges().changes().containsKey(copy.id())||!context.originals().findById(copy.id()).contains(copy)||!copies.add(copy.id())){diagnostics.add(error(ExportDiagnostic.Code.EXPORT_INVALID_RECIPE,"Copy must be an explicit unchanged original with no saved change",context));continue;}
            try{validator.verifySource(OriginalRecipeReference.from(copy),context);}catch(java.io.IOException failure){diagnostics.add(error(ExportDiagnostic.Code.EXPORT_SOURCE_CONFLICT,failure.getMessage(),context));}
            changes.put(copy.id(),new RecipeChange(copy.id(),RecipeChangeType.CREATE,List.of(),List.of(copy),Instant.EPOCH,Map.of("mode","unchanged-copy")));
        }
        if(!context.modpack().minecraftVersion().value().equals(context.profile().minecraftVersion())||!context.modpack().loader().type().name().equals(context.profile().loader()))diagnostics.add(error(ExportDiagnostic.Code.EXPORT_VERSION_MISMATCH,"Profile version/loader does not match imported instance",context));
        for(final var change:changes.values()) {
            SafeFiles.checkCancelled();diagnostics.addAll(validator.check(change,context,copies.contains(change.id())));
            final var supported=exporters.stream().filter(exporter->exporter.checkCapability(change,context).supported()).map(RecipeExporter::format).toList();
            ExportFormat choice=request.choices().getOrDefault(change.id(),context.profile().preferredFormat());
            if(choice==ExportFormat.AUTO) {
                // Defaults are preferences, while all supported alternatives remain visible and selectable.
                choice=supported.contains(ExportFormat.DATAPACK)?ExportFormat.DATAPACK:supported.contains(ExportFormat.KUBEJS)?ExportFormat.KUBEJS:supported.stream().findFirst().orElse(ExportFormat.AUTO);
            }
            final var selected=choice;final var exporter=exporters.stream().filter(e->e.format()==selected).findFirst();
            final var capability=exporter.map(e->e.checkCapability(change,context)).orElseGet(()->ExportCapability.unsupported("No compatible exporter. "+exporters.stream().map(e->e.format()+": "+e.checkCapability(change,context).reason()).collect(java.util.stream.Collectors.joining("; "))));
            planned.add(new PlannedRecipe(change,choice,capability,supported));
            if(!capability.supported()&&(choice==ExportFormat.KUBEJS||choice==ExportFormat.CRAFTTWEAKER||choice==ExportFormat.AUTO)) {
                final var candidates=choice==ExportFormat.AUTO?List.of(ExportFormat.KUBEJS,ExportFormat.CRAFTTWEAKER):List.of(choice);
                for(final var candidate:candidates) {
                    final String mod=candidate==ExportFormat.KUBEJS?"kubejs":"crafttweaker";
                    if(context.modVersion(mod).equals("UNKNOWN"))diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.ERROR,ExportDiagnostic.Code.EXPORT_MISSING_DEPENDENCY,"Dependency absent or version unconfirmed: "+mod,List.of(change.id()),candidate,"Import a compatible instance with confirmed dependency metadata"));
                    else if(!context.modVersion(mod).equals(candidate==ExportFormat.KUBEJS?"2001.6.4-build.120":"14.0.57"))diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.ERROR,ExportDiagnostic.Code.EXPORT_VERSION_MISMATCH,"Unverified "+mod+" API version: "+context.modVersion(mod),List.of(change.id()),candidate,"Use a verified adapter/version"));
                }
            }
            if(!capability.supported())diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.ERROR,ExportDiagnostic.Code.EXPORT_UNSUPPORTED_RECIPE_TYPE,capability.reason(),List.of(change.id()),choice,"Choose a supported format/version or adapter; this recipe is not silently skipped"));
            if(context.profile().preferredFormat()==ExportFormat.AUTO&&supported.size()>1)diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.INFO,ExportDiagnostic.Code.EXPORT_AMBIGUOUS_CHOICE,"AUTO preference selected "+choice+"; alternatives="+supported,List.of(change.id()),choice,"Review/choose format per recipe in the preview"));
        }
        if(changes.size()>10000 || changes.values().stream().flatMap(c->c.replacement().stream()).mapToLong(r->r.rawJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum()>16L*1024*1024) {
            diagnostics.add(error(ExportDiagnostic.Code.EXPORT_INVALID_RECIPE,"Export input batch exceeds 10000 recipes / 16 MiB JSON; use a smaller profile selection",context));
            return new ExportPlan(request,planned,Map.of(),Map.of(),Set.of(),diagnostics);
        }
        for(final var exporter:exporters) {
            final var batch=planned.stream().filter(p->p.format()==exporter.format()&&p.capability().supported()).map(PlannedRecipe::change).toList();
            try{exporter.generate(batch,context).forEach((path,text)->{ExportPaths.relative(path);if(files.putIfAbsent(path,text)!=null)throw new IllegalArgumentException("Two exporters target "+path);});}
            catch(RuntimeException failure){diagnostics.add(error(ExportDiagnostic.Code.EXPORT_INVALID_RECIPE,"Generation failed: "+failure.getMessage(),context));}
        }
        final long bytes=files.values().stream().mapToLong(value->value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
        if(files.size()>10000||bytes>48L*1024*1024||files.values().stream().anyMatch(value->value.length()>16*1024*1024))diagnostics.add(error(ExportDiagnostic.Code.EXPORT_INVALID_RECIPE,"Generated file/count/byte limit exceeded",context));
        final var expected=new HashMap<String,String>();final var obsolete=new TreeSet<String>();
        try {
            final var target=context.profile().targetDirectory().toRealPath();final var source=context.modpack().selectedDirectory().toRealPath();
            if(!Files.isDirectory(target)||!Files.isWritable(target))throw new java.io.IOException("Output directory is not writable");
            if(target.startsWith(source)&&!context.allowSourceDirectory())throw new java.io.IOException("Writing into imported modpack requires explicit consent in this export session");
            final var previous=context.previousManifests().stream().filter(m->m.profileId().equals(context.profile().id())&&m.targetDirectory().toAbsolutePath().normalize().equals(target)).reduce((a,b)->b);
            final var owned=previous.map(ExportManifest::fileHashes).orElse(Map.of());
            owned.keySet().forEach(path->ExportPaths.managed(context.profile().id(),path));
            if(previous.isPresent()&&!new HashSet<>(previous.get().recipeIds()).equals(changes.keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet())))diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.WARNING,ExportDiagnostic.Code.EXPORT_FILE_CONFLICT,"This profile replaces its previous export selection, including grouped scripts. Previously exported operations not selected now will be removed from generated files.",List.of(),context.profile().preferredFormat(),"Use separate profiles for independent subsets; review file contents"));obsolete.addAll(owned.keySet());obsolete.removeAll(files.keySet());
            final var touched=new TreeSet<>(files.keySet());touched.addAll(obsolete);
            for(final var path:touched) {
                final var file=ExportPaths.inside(target,path);
                if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
                    final String current=ExportPaths.hash(file);expected.put(path,current);
                    if(!Objects.equals(owned.get(path),current))diagnostics.add(error(ExportDiagnostic.Code.EXPORT_FILE_CONFLICT,"Unowned or user-modified file: "+path,context));
                    else if(context.profile().overwritePolicy()==OverwritePolicy.REFUSE && (!files.containsKey(path)||!ExportText.hash(files.get(path)).equals(current)))diagnostics.add(error(ExportDiagnostic.Code.EXPORT_FILE_CONFLICT,"Profile refuses replacement/removal: "+path,context));
                }else{expected.put(path,"");if(obsolete.contains(path))obsolete.remove(path);}
            }
        }catch(Exception failure){diagnostics.add(error(ExportDiagnostic.Code.EXPORT_FILE_CONFLICT,failure.getMessage(),context));}
        if(changes.isEmpty()&&obsolete.isEmpty())diagnostics.add(error(ExportDiagnostic.Code.EXPORT_INVALID_RECIPE,"No saved changes or explicit copy selected",context));
        diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.WARNING,ExportDiagnostic.Code.EXPORT_RUNTIME_UNVERIFIED,"Dry Run/file generation does not validate loading in Minecraft. Other scripts and datapack priorities may change the result.",List.of(),context.profile().preferredFormat(),"Review and run an isolated runtime integration test before installing into a real instance"));
        return new ExportPlan(request,planned,files,expected,obsolete,diagnostics);
    }
    private ExportDiagnostic error(final ExportDiagnostic.Code code,final String message,final ExportContext context){return new ExportDiagnostic(ExportDiagnostic.Level.ERROR,code,message,List.of(),context.profile().preferredFormat(),"Resolve diagnostics and rebuild the preview");}
}
