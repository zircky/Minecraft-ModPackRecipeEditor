package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeSourceResolverTest {
    private ResourceDocument document(final String recipe,final String mod,final String jar) {
        final var id=ResourceLocation.parse(recipe);
        return new ResourceDocument(new ResourceSource(SourceType.MOD_JAR,List.of(mod),Path.of(jar),
                "data/"+id.namespace()+"/recipes/"+id.path()+".json","Static resource scan"),"{\"type\":\"unknown:recipe\"}");
    }
    @Test void namespaceNeverBecomesContainerModOrAuthor() {
        final var documents=List.of(document("minecraft:tnt","minecraft","vanilla.jar"),
                document("example:machine","example","example.jar"),document("minecraft:tnt","biomesoplenty","bop.jar"));
        final var recipes=new StaticRecipeScanner(List.of(),ResourceLayout.minecraft1201()).scan(documents,new ArrayList<>());
        final var index=new RecipeIndex(recipes,new TagIndex(List.of()));
        assertEquals(2,index.byNamespace("minecraft").size());assertEquals(1,index.bySourceMod("minecraft").size());
        final var bop=index.bySourceMod("biomesoplenty").getFirst();
        assertEquals("minecraft",bop.id().namespace());assertEquals(List.of("biomesoplenty"),bop.source().archiveModIds());
        assertEquals("data/minecraft/recipes/tnt.json",bop.source().originalFile());
        assertEquals("UNKNOWN",bop.source().declaringSource());assertTrue(index.provenance(bop.id()).activeDefinition().isEmpty());
        assertEquals(2,index.provenance(bop.id()).discoveredDefinitions().size());
        assertTrue(RecipeFilterMode.RECIPE_NAMESPACE.matches(bop,"minecraft"));
        assertFalse(RecipeFilterMode.SOURCE_MOD.matches(bop,"minecraft"));assertTrue(RecipeFilterMode.SOURCE_MOD.matches(bop,"biomesoplenty"));
        assertEquals(List.of(bop),recipes.stream().filter(recipe->RecipeFilterMode.SOURCE_MOD.matches(recipe,"biomesoplenty")).toList());
    }
    @Test void interleavedDocumentsKeepTheirOwnJarAndImmutableMetadata() {
        final var documents=List.of(document("a:first","a","a.jar"),document("b:first","b","b.jar"),document("a:last","a","a.jar"));
        final var scanned=new StaticRecipeScanner(List.of(),ResourceLayout.minecraft1201()).scan(documents,new ArrayList<>());
        assertEquals(List.of("a.jar","b.jar","a.jar"),scanned.stream().map(r->r.source().physicalSource()).toList());
        assertEquals(List.of(List.of("a"),List.of("b"),List.of("a")),scanned.stream().map(r->r.source().sourceModIds()).toList());
        assertThrows(UnsupportedOperationException.class,()->scanned.getFirst().source().modIds().add("b"));
    }
    @Test void datapackCandidateDoesNotEstablishLoadOrder() {
        final var id=ResourceLocation.parse("minecraft:tnt");
        final var jar=RecipeSource.from(document(id.toString(),"biomesoplenty","bop.jar").source());
        final var pack=new RecipeSource(SourceType.DATAPACK,List.of(),Path.of("datapacks/custom"),"data/minecraft/recipes/tnt.json","Local scan");
        final var resolved=new RecipeSourceResolver().discover(id,List.of(jar,pack));
        final var reverse=new RecipeSourceResolver().discover(id,List.of(pack,jar));
        assertTrue(resolved.activeDefinition().isEmpty());assertTrue(reverse.activeDefinition().isEmpty());
        assertTrue(resolved.overriddenDefinitions().isEmpty());assertEquals(Set.of(jar,pack),Set.copyOf(resolved.discoveredDefinitions()));
        assertEquals("UNKNOWN",pack.declaringSource());assertTrue(pack.sourceModIds().isEmpty());
    }
}
