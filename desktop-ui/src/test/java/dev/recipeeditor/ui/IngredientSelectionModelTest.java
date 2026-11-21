package dev.recipeeditor.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class IngredientSelectionModelTest {
    @Test void preservesOpaqueAlternativesUnlessExplicitlyReplaced()throws Exception {
        final String json="[{\"item\":\"test:a\",\"nbt\":{\"keep\":1}},{\"tag\":\"forge:ingots\"},{\"custom\":true}]";
        final var value=new IngredientSelectionModel(json);final var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(mapper.readTree(json),mapper.readTree(value.json()));
        value.replace(1,"{\"tag\":\"forge:gems\"}");
        assertEquals(mapper.readTree(json).get(0),mapper.readTree(value.json()).get(0));
        assertEquals(mapper.readTree(json).get(2),mapper.readTree(value.json()).get(2));
        value.remove(1);assertEquals(2,value.choices().size());value.add("{\"item\":\"test:b\"}");assertEquals(3,value.choices().size());
    }
    @Test void emptySelectionCannotSilentlySave(){assertThrows(IllegalArgumentException.class,()->new IngredientSelectionModel("").json());}
}
