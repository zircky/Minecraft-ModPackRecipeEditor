package dev.recipeeditor.companionclient;
import dev.recipeeditor.companionprotocol.RuntimeData;
import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in assertions against a checksummed snapshot actually collected from Minecraft, never synthetic GT returns. */
@EnabledIfSystemProperty(named="runtimeSnapshotFile",matches=".+")
@org.junit.jupiter.api.Tag("external")
final class GregTechRuntimeRecipeAdapterTest {
    private List<RuntimeData.Recipe> recipes()throws Exception {
        final var snapshot=new RuntimeSnapshotService().load(Path.of(System.getProperty("runtimeSnapshotFile")));
        Assumptions.assumeTrue(snapshot.mods().stream().anyMatch(m->m.id().equals("gtceu")&&m.version().equals("7.1.4")),"GTCEu 7.1.4 absent from actual runtime snapshot");
        final var recipes=snapshot.recipes().stream().filter(r->r.adapter().equals("gtceu-7.1.4")).toList();assertFalse(recipes.isEmpty());return recipes;
    }
    @Test void itemInputsAndOutputs()throws Exception {final var recipes=recipes();assertTrue(recipes.stream().anyMatch(r->has(r,"inputs","item")));assertTrue(recipes.stream().anyMatch(r->has(r,"outputs","item")));}
    @Test void fluidInputsAndOutputs()throws Exception {final var recipes=recipes();assertTrue(recipes.stream().anyMatch(r->has(r,"inputs","fluid")));assertTrue(recipes.stream().anyMatch(r->has(r,"outputs","fluid")));}
    @Test void durationAndEUtAreReceived()throws Exception {assertTrue(recipes().stream().anyMatch(r->{final var p=r.properties();return p.has("duration")&&p.get("duration").getAsInt()>0&&p.has("inputEUt")&&p.get("inputEUt").getAsLong()>0;}));}
    @Test void nonConsumableInputsStayDistinct()throws Exception {assertTrue(recipes().stream().anyMatch(r->contents(r,"inputs").stream().anyMatch(c->c.has("chance")&&c.get("chance").getAsInt()==0)));}
    @Test void chancedOutputsRetainChanceMaxAndBoost()throws Exception {assertTrue(recipes().stream().anyMatch(r->contents(r,"outputs").stream().anyMatch(c->c.has("chance")&&c.has("maxChance")&&c.has("tierChanceBoost")&&c.get("chance").getAsInt()>0&&c.get("chance").getAsInt()<c.get("maxChance").getAsInt())));}
    @Test void conditionsCategoryAndExtraDataAreAvailable()throws Exception {final var recipes=recipes();assertTrue(recipes.stream().anyMatch(r->r.properties().has("recipeCategory")));assertTrue(recipes.stream().anyMatch(r->{final var p=r.properties();return p.has("recipeConditions")&&p.getAsJsonArray("recipeConditions").size()>0;}));assertTrue(recipes.stream().anyMatch(r->{final var p=r.properties();return p.has("data")&&p.getAsJsonObject("data").size()>0;}));}
    private boolean has(final RuntimeData.Recipe r,final String role,final String capability){final var p=r.properties();return p.has(role)&&p.getAsJsonObject(role).has(capability)&&p.getAsJsonObject(role).getAsJsonArray(capability).size()>0;}
    private List<JsonObject> contents(final RuntimeData.Recipe r,final String role){final var p=r.properties();final var result=new ArrayList<JsonObject>();if(p.has(role))p.getAsJsonObject(role).entrySet().forEach(e->e.getValue().getAsJsonArray().forEach(value->result.add(value.getAsJsonObject())));return result;}
}
