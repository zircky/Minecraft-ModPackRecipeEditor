package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ResourceBrowserViewModelTest {
    private RegistryEntry entry(final Map<String,String> names) {
        return new RegistryEntry(ResourceLocation.parse("minecraft:very_long_resource_id_that_must_remain_whole"),ResourceKind.ITEM,"not a verified translation","actual.mod.key",names,
                List.of(new ResourceSource(SourceType.MOD_JAR,List.of("differentmod"),Path.of("mod.jar"),"assets/minecraft/model.json","static")),List.of(),DiscoveryConfidence.INFERRED_FROM_RESOURCES,"static");
    }
    @Test void selectedLanguageEnglishFallbackThenFullIdWithoutInventedTitleCase() {
        final var model=new ResourceBrowserViewModel(new RegistryIndex(List.of()));
        assertEquals("Русский",model.displayName(entry(Map.of("ru_ru","Русский","en_us","English")),"ru_ru"));
        assertEquals("English",model.displayName(entry(Map.of("en_us","English")),"ru_ru"));
        assertEquals(entry(Map.of()).id().toString(),model.displayName(entry(Map.of()),"en_us"));
        assertEquals("",model.secondaryId(entry(Map.of()),"ru_ru"));
        assertEquals(entry(Map.of()).id().toString(),model.secondaryId(entry(Map.of("en_us","English")),"ru_ru"));
        assertEquals(1,model.tooltip(entry(Map.of()),"en_us").split(java.util.regex.Pattern.quote(entry(Map.of()).id().toString()),-1).length-1);
    }
    @Test void namespaceAndPhysicalSourceModFiltersRemainDistinctAndDetailsAreComplete() {
        final var entry=entry(Map.of("en_us","A name"));final var model=new ResourceBrowserViewModel(new RegistryIndex(List.of(entry)));
        assertEquals(1,model.search(ResourceKind.ITEM,"",ResourceBrowserViewModel.FilterMode.NAMESPACE,"minecraft").size());
        assertEquals(1,model.search(ResourceKind.ITEM,"",ResourceBrowserViewModel.FilterMode.SOURCE_MOD,"differentmod").size());
        assertTrue(model.search(ResourceKind.ITEM,"",ResourceBrowserViewModel.FilterMode.SOURCE_MOD,"minecraft").isEmpty());
        final String details=model.details(entry,"en_us");for(final String field:List.of(entry.id().toString(),"actual.mod.key","Runtime confirmed: false","Texture sources:","differentmod"))assertTrue(details.contains(field),field);
    }
    @Test void materialFormNavigationRequiresMatchingConfirmedKindAndId() {
        final var id=ResourceLocation.parse("gtceu:dynamic");
        final var entry=new RegistryEntry(id,ResourceKind.ITEM,"Dynamic","",Map.of(),List.of(new ResourceSource(SourceType.RUNTIME,List.of(),Path.of("snapshot"),"registry/item","runtime")),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"runtime");
        final var model=new ResourceBrowserViewModel(new RegistryIndex(List.of(entry)));
        assertTrue(model.form(new MaterialDefinition.Form("ingot",ResourceKind.ITEM,id)).isPresent());
        assertTrue(model.form(new MaterialDefinition.Form("fluid",ResourceKind.FLUID,id)).isEmpty());
        assertTrue(model.form(new MaterialDefinition.Form("plate",ResourceKind.ITEM,ResourceLocation.parse("gtceu:not_registered"))).isEmpty());
    }
}
