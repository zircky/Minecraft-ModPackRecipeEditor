package dev.recipeeditor.exporters;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class ExportFixtures {
    final Path root,source,target,project;
    final RecipeValidator validator=new RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),new RegistryIndex(List.of()));
    final ExportPlanner planner=new ExportPlanner(List.of(new DatapackRecipeExporter(),new KubeJsRecipeExporter(),new CraftTweakerRecipeExporter()),new ExportValidator(validator));
    final String raw="{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"forge:ingots/iron\"}],\"result\":{\"item\":\"minecraft:iron_block\",\"count\":2}}";
    ExportFixtures(final Path root)throws Exception {this.root=root;source=Files.createDirectories(root.resolve("source"));target=Files.createDirectories(root.resolve("output"));project=root.resolve("project.json");}
    RecipeDefinition recipe(final String id,final String json) {final var location=ResourceLocation.parse(id);return validator.validate(location,json,new RecipeSource(SourceType.LOCAL_RESOURCE,List.of("source_owner"),source,"data/"+location.namespace()+"/recipes/"+location.path()+".json","test fixture")).recipe();}
    RecipeDefinition original(final String id,final String json)throws Exception {final var value=recipe(id,json);final var file=source.resolve(value.source().internalPath());Files.createDirectories(file.getParent());Files.writeString(file,json);return value;}
    RecipeChange create(final String id,final String json) {final var r=recipe(id,json);return new RecipeChange(r.id(),RecipeChangeType.CREATE,List.of(),List.of(r),Instant.EPOCH,Map.of());}
    RecipeChange change(final RecipeDefinition original,final RecipeChangeType operation,final String replacement) {return new RecipeChange(original.id(),operation,List.of(OriginalRecipeReference.from(original)),operation==RecipeChangeType.DELETE?List.of():List.of(recipe(original.id().toString(),replacement)),Instant.EPOCH,Map.of());}
    ExportContext context(final List<RecipeChange> changes,final List<RecipeDefinition> originals,final ExportFormat format,final Map<String,String> mods,final List<ExportManifest> manifests,final OverwritePolicy policy) {
        final var jars=new ArrayList<ModScanResult>(); mods.forEach((id,version)->{final var path=source.resolve(id+".jar");final var metadata=new ModMetadata(id,id,version,"",List.of(),List.of(),ModLoaderType.FORGE,"test metadata","");jars.add(new ModScanResult(path,List.of(new ModDefinition(metadata,path,ScanStatus.OK,List.of())),ScanStatus.OK,List.of()));});
        final var pack=new ModpackDefinition(source,source,"test",new MinecraftVersion("1.20.1",List.of("test")),new ModLoaderDefinition(ModLoaderType.FORGE,"47.3.0",List.of("test")),jars);
        final var saved=new LinkedHashMap<ResourceLocation,RecipeChange>();changes.forEach(c->saved.put(c.id(),c));
        return new ExportContext(pack,new RecipeIndex(originals,new TagIndex(List.of())),new RecipeChangeSet(saved),project,new ExportProfile("testprofile","Test",target,format,policy,true,"1.20.1","FORGE"),manifests,Set.of(),false);
    }
    ExportContext context(final List<RecipeChange> changes,final ExportFormat format) {return context(changes,List.of(),format,Map.of("kubejs","2001.6.4-build.120","crafttweaker","14.0.57","gtceu","7.1.4"),List.of(),OverwritePolicy.OWNED_UNCHANGED);}
    ExportRequest request(final ExportContext c) {return new ExportRequest(c,c.savedChanges().changes().keySet(),Map.of(),List.of());}
    ExportPlan plan(final List<RecipeChange> changes,final ExportFormat format) {return planner.plan(request(context(changes,format)));}
    ExportManifest manifest(final ExportPlan plan) {final var hashes=new TreeMap<String,String>();plan.files().forEach((path,text)->hashes.put(path,ExportText.hash(text)));return new ExportManifest("testprofile",target,Set.of(ExportFormat.DATAPACK),"1.20.1",Instant.EPOCH,hashes,plan.recipes().stream().map(r->r.change().id().toString()).toList(),"GENERATED_RUNTIME_UNVERIFIED");}
}
