package dev.recipeeditor.companionclient;
import dev.recipeeditor.companionprotocol.RuntimeData;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="runtimeSnapshotFile",matches=".+")
@org.junit.jupiter.api.Tag("external")
final class RuntimeRecipeAdapterTest {
    @Test void actualCookingRecipeKeepsLoadedTimeExperienceInputsAndResult()throws Exception {final var data=new RuntimeSnapshotService().load(Path.of(System.getProperty("runtimeSnapshotFile")));assertTrue(data.recipes().stream().filter(r->r.adapter().equals("vanilla-1.20.1")&&r.type().equals("minecraft:smelting")).anyMatch(r->{final var p=r.properties();return p.has("cookingTime")&&p.has("experience")&&p.has("ingredients")&&p.has("result")&&p.has("sourceJsonAvailable")&&!p.get("sourceJsonAvailable").getAsBoolean();}));}
    @Test void unknownRecipesNeverAcquireGuessedIngredientsOrEditableSourceJson()throws Exception {final var data=new RuntimeSnapshotService().load(Path.of(System.getProperty("runtimeSnapshotFile")));final var unknown=data.recipes().stream().filter(r->r.adapter().equals("generic")).toList();Assumptions.assumeFalse(unknown.isEmpty(),"No unknown recipes in this actual environment");assertTrue(unknown.stream().allMatch(r->r.status()==RuntimeData.Support.UNSUPPORTED&&r.properties().size()==0&&!r.diagnostics().isEmpty()));}
}
