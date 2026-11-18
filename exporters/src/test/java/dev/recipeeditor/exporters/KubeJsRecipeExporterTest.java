package dev.recipeeditor.exporters;

import java.nio.file.Path;
import java.util.*;
import dev.recipeeditor.recipeengine.editor.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class KubeJsRecipeExporterTest {
    @TempDir Path root;
    @Test void createUpdateDeleteAreGroupedAndDeterministic()throws Exception {
        final var f=new ExportFixtures(root);final var base=f.original("test:base",f.raw);final var deleted=f.original("test:delete",f.raw);
        final var changes=List.of(f.create("test:new",f.raw),f.change(base,RecipeChangeType.UPDATE,f.raw),f.change(deleted,RecipeChangeType.DELETE,""));
        final var ctx=f.context(changes,List.of(base,deleted),ExportFormat.KUBEJS,Map.of("kubejs","2001.6.4-build.120"),List.of(),OverwritePolicy.OWNED_UNCHANGED);
        final var plan=f.planner.plan(f.request(ctx));assertTrue(plan.valid(),plan.diagnostics().toString());assertEquals(1,plan.files().size());final var script=plan.files().values().iterator().next();
        assertTrue(script.contains("ServerEvents.recipes"));assertTrue(script.contains("event.remove({id: \"test:base\"})"));assertTrue(script.contains("event.remove({id: \"test:delete\"})"));assertTrue(script.contains(".id(\"test:new\")"));
        assertEquals(2,script.split("event.custom",-1).length-1);assertTrue(script.contains("forge:ingots/iron"));assertTrue(script.contains("\\\"count\\\":2"));assertEquals(plan.files(),f.planner.plan(f.request(ctx)).files());
    }
    @Test void userStringsRemainDataIncludingQuotesAndLineSeparators()throws Exception {
        final var f=new ExportFixtures(root);final var text=new ExportText();final String attack="'); Java.loadClass('evil'); //\n\\\"\u2028\u2029";
        final var tree=text.json(f.raw);((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("group",attack);final String json=tree.toString();
        final var plan=f.plan(List.of(f.create("test:quoted",json)),ExportFormat.KUBEJS);assertTrue(plan.valid(),plan.diagnostics().toString());
        final var script=plan.files().values().iterator().next();final String prefix="event.custom(JSON.parse(";final int start=script.indexOf(prefix)+prefix.length();final int end=script.indexOf(")).id(",start);final var literal=text.json(script.substring(start,end));
        assertEquals(json,literal.asText());assertEquals(attack,text.json(literal.asText()).path("group").asText());assertFalse(script.contains("\u2028"));
    }
    @Test void missingAndUnverifiedVersionsAreRejected()throws Exception {final var f=new ExportFixtures(root);final var c=f.create("test:new",f.raw);final var e=new KubeJsRecipeExporter();assertFalse(e.checkCapability(c,f.context(List.of(c),List.of(),ExportFormat.KUBEJS,Map.of(),List.of(),OverwritePolicy.REFUSE)).supported());assertFalse(e.checkCapability(c,f.context(List.of(c),List.of(),ExportFormat.KUBEJS,Map.of("kubejs","UNKNOWN"),List.of(),OverwritePolicy.REFUSE)).supported());}
    @Test void partialCustomIngredientsAreNotSilentlyDowngraded()throws Exception {final var f=new ExportFixtures(root);assertFalse(f.plan(List.of(f.create("test:new",f.raw.replace("{\"tag\":\"forge:ingots/iron\"}","{\"type\":\"forge:nbt\",\"item\":\"minecraft:iron_ingot\",\"nbt\":{}}"))),ExportFormat.KUBEJS).valid());}
}
