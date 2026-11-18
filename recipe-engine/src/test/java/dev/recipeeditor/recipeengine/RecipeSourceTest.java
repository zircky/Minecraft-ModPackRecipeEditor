package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeSourceTest {
    @Test void conflictsPreserveAllSourcesWithoutWinner() {
        final var first=RecipeFixtures.recipe("test:same","one.jar",List.of(),List.of(),RecipeParseStatus.UNSUPPORTED);
        final var second=RecipeFixtures.recipe("test:same","two.jar",List.of(),List.of(),RecipeParseStatus.UNSUPPORTED);
        final var index=new RecipeIndex(List.of(first,second),new TagIndex(List.of()));
        final var matches=index.findById(ResourceLocation.parse("test:same")); assertEquals(2,matches.size());
        assertNotEquals(matches.get(0).source().archivePath(),matches.get(1).source().archivePath());
        assertEquals(1,index.conflicts().size()); assertEquals("{raw}",matches.getFirst().rawJson());
    }
    @Test void runtimeIsReservedForFutureProducers() {
        final var source=new RecipeSource(SourceType.RUNTIME,List.of(),Path.of("runtime"),"","Future companion");
        assertEquals(SourceType.RUNTIME,source.type());
        assertEquals(SourceType.MOD_JAR,RecipeFixtures.recipe("test:id","a.jar",List.of(),List.of(),RecipeParseStatus.UNSUPPORTED).source().type());
    }
}
