package dev.recipeeditor.recipeengine.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecipeDiffServiceTest {
    @Test void structuralChangesIncludeInputsOutputCountsAndParameters() {
        final var changes=new RecipeDiffService().compare("{\"ingredients\":[{\"item\":\"a:a\"}],\"result\":{\"item\":\"a:b\",\"count\":1},\"cookingtime\":200}","{\"ingredients\":[{\"item\":\"a:c\"}],\"result\":{\"item\":\"a:d\",\"count\":2},\"cookingtime\":100}");
        assertEquals(4,changes.size()); assertTrue(changes.stream().anyMatch(d->d.path().equals("$/result/count")));
    }
    @Test void formattingAndKeyOrderAreNotSemanticChanges() { assertTrue(new RecipeDiffService().compare("{\"a\":1,\"b\":2}","{ \"b\":2, \"a\":1 }").isEmpty()); }
}
