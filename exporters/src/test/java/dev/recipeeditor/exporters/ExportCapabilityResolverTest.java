package dev.recipeeditor.exporters;

import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.SourceType;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class ExportCapabilityResolverTest {
    @TempDir Path root;
    private final List<RecipeExporter> exporters=List.of(new DatapackRecipeExporter(),new KubeJsRecipeExporter(),new CraftTweakerRecipeExporter());
    private final ExportCapabilityResolver resolver=new ExportCapabilityResolver(exporters);

    @ParameterizedTest
    @ValueSource(strings={"crafting_shaped","crafting_shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","smithing_transform","smithing_trim"})
    void allVanillaOperationsUseSerializerNotRecipeNamespaceOrPhysicalMod(final String type) throws Exception {
        final var f=new ExportFixtures(root);
        final var helper=new KubeJsTestSupport();
        final var parsed=f.original("minecraft:nested/"+type,helper.recipe(type));
        final var physical=new RecipeSource(SourceType.MOD_JAR,List.of("biomesoplenty"),root.resolve("bop.jar"),parsed.source().internalPath(),"Static scan");
        final var original=withMetadata(parsed,parsed.type(),physical,parsed.rawJson());
        for(final var operation:RecipeChangeType.values()) {
            final var change=new RecipeChange(original.id(),operation,
                    operation==RecipeChangeType.CREATE?List.of():List.of(OriginalRecipeReference.from(original)),
                    operation==RecipeChangeType.DELETE?List.of():List.of(original),java.time.Instant.EPOCH,Map.of());
            final var originals=operation==RecipeChangeType.CREATE?List.<RecipeDefinition>of():List.of(original);
            for(final var mode:KubeJsExportMode.values()) {
                final var context=helper.context(f,List.of(change),originals,mode);
                final var capability=resolver.resolve(change,context,ExportFormat.KUBEJS);
                final boolean expected=operation!=RecipeChangeType.DELETE || mode!=KubeJsExportMode.DATA_JSON;
                assertEquals(expected,capability.supported(),type+" "+operation+" "+mode+": "+capability.reason());
                assertFalse(capability.reason().isBlank());
            }
        }
    }

    @Test void staleParsedMetadataCannotHideInvalidJsonOrDifferentSerializer() throws Exception {
        final var f=new ExportFixtures(root);
        final var original=f.recipe("test:new",f.raw);
        for(final String raw:List.of("{}",f.raw.replace("crafting_shapeless","smelting"),f.raw.replace("\"count\":2","\"count\":0"))) {
            final var forged=withMetadata(original,original.type(),original.source(),raw);
            final var change=new RecipeChange(forged.id(),RecipeChangeType.CREATE,List.of(),List.of(forged),java.time.Instant.EPOCH,Map.of());
            final var context=f.context(List.of(change),ExportFormat.KUBEJS);
            for(final var exporter:exporters)assertFalse(resolver.resolve(change,context,exporter.format()).supported(),raw);
        }
    }

    @Test void parsedCookingObjectIsNotAVerified1201Result() throws Exception {
        final var f=new ExportFixtures(root);
        final String json=new KubeJsTestSupport().recipe("smelting").replace("\"result\":\"minecraft:iron_ingot\"","\"result\":{\"item\":\"minecraft:iron_ingot\",\"count\":2}");
        final var change=f.create("test:smelting",json);
        for(final var exporter:exporters) {
            final var capability=resolver.resolve(change,f.context(List.of(change),ExportFormat.KUBEJS),exporter.format());
            assertFalse(capability.supported());
            assertTrue(capability.reason().contains("cooking result"),capability.reason());
        }
    }

    @Test void conflictsAndMissingOriginalsCannotAppearSupportedInPreview() throws Exception {
        final var f=new ExportFixtures(root);
        final var original=f.original("minecraft:tnt",f.raw);
        final var change=f.change(original,RecipeChangeType.UPDATE,f.raw);
        final var missing=f.context(List.of(change),ExportFormat.KUBEJS);
        assertFalse(resolver.resolve(change,missing,ExportFormat.KUBEJS).supported());
        final var base=new KubeJsTestSupport().context(f,List.of(change),List.of(original),KubeJsExportMode.JAVASCRIPT);
        final var blocked=new ExportContext(base.modpack(),base.originals(),base.savedChanges(),base.projectFile(),base.profile(),List.of(),Set.of(change.id()),false);
        final var plan=f.planner.plan(f.request(blocked));
        assertFalse(plan.valid());
        assertFalse(plan.recipes().getFirst().capability().supported());
        assertTrue(plan.recipes().getFirst().alternatives().isEmpty());
        assertTrue(plan.recipes().getFirst().targetFiles().isEmpty());
        assertTrue(plan.files().isEmpty());
    }

    @Test void profileMismatchUnknownDependencyAndUnsupportedIntegrationsRemainExplicit() throws Exception {
        final var f=new ExportFixtures(root);
        final var helper=new KubeJsTestSupport();
        final var change=f.create("test:new",f.raw);
        final var base=helper.context(f,List.of(change),List.of(),KubeJsExportMode.JAVASCRIPT);
        final var pack=base.modpack();
        for(final var version:List.of("1.20.1","1.21.1")) {
            final var altered=new ModpackDefinition(pack.selectedDirectory(),pack.gameDirectory(),pack.structure(),new MinecraftVersion(version,List.of("test")),pack.loader(),pack.jars());
            final var context=new ExportContext(altered,base.originals(),base.savedChanges(),base.projectFile(),base.profile(),List.of(),Set.of(),false);
            assertEquals(version.equals("1.20.1"),resolver.resolve(change,context,ExportFormat.KUBEJS).supported());
        }
        final var absent=f.context(List.of(change),List.of(),ExportFormat.KUBEJS,Map.of(),List.of(),OverwritePolicy.REFUSE);
        assertFalse(resolver.resolve(change,absent,ExportFormat.KUBEJS).supported());
        for(final String type:List.of("gtceu:assembler","create:sequenced_assembly","mekanism:chemical_infusing")) {
            final var custom=f.create("minecraft:custom",f.raw.replace("minecraft:crafting_shapeless",type));
            final var context=helper.context(f,List.of(custom),List.of(),KubeJsExportMode.JAVASCRIPT);
            assertFalse(resolver.resolve(custom,context,ExportFormat.KUBEJS).supported(),type);
        }
        final var unknown=new ExportCapabilityResolver(List.of()).resolve(change,base,ExportFormat.KUBEJS);
        assertEquals(ExportSupportStatus.UNKNOWN,unknown.status());
        assertFalse(unknown.supported());
        assertFalse(unknown.reason().isBlank());
    }

    @Test void adapterOperationSetIsEnforced() throws Exception {
        final var f=new ExportFixtures(root);
        final var original=f.original("test:old",f.raw);
        final var change=f.change(original,RecipeChangeType.DELETE,"");
        final RecipeExporter restricted=new RecipeExporter() {
            @Override public ExportFormat format(){return ExportFormat.KUBEJS;}
            @Override public ExportCapability checkCapability(final RecipeChange c,final ExportContext context){return new ExportCapability(ExportSupportStatus.SUPPORTED,"CREATE only",Set.of(RecipeChangeType.CREATE),Map.of());}
            @Override public Map<String,String> generate(final List<RecipeChange> c,final ExportContext context){throw new AssertionError("Capability resolution must not generate files");}
        };
        final var context=new KubeJsTestSupport().context(f,List.of(change),List.of(original),KubeJsExportMode.JAVASCRIPT);
        assertFalse(new ExportCapabilityResolver(List.of(restricted)).resolve(change,context,ExportFormat.KUBEJS).supported());
    }

    private RecipeDefinition withMetadata(final RecipeDefinition recipe,final RecipeTypeDefinition type,final RecipeSource source,final String raw) {
        return new RecipeDefinition(recipe.id(),type,recipe.inputs(),recipe.outputs(),recipe.layout(),recipe.properties(),source,raw,RecipeParseStatus.PARSED,List.of());
    }
}

