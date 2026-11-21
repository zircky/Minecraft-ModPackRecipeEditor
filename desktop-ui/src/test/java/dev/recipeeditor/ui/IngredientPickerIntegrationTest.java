package dev.recipeeditor.ui;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") final class IngredientPickerIntegrationTest {
    @Test void acceptingExistingAlternativesDoesNotFlattenOrAppendDuplicateChoices()throws Exception {
        FxLayoutTestSupport.run(()->{
            javafx.application.Platform.setImplicitExit(false);
            final String json="[{\"item\":\"test:a\",\"custom\":true},{\"tag\":\"forge:ingots\"}]";
            final var picker=new IngredientPicker(null,FxLayoutTestSupport.discovery(),dev.recipeeditor.registry.ResourceKind.ITEM,true,false,json);
            try{picker.show();((javafx.scene.control.Button)picker.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK)).fire();assertNotNull(picker.getResult());assertEquals(json,picker.getResult().json());}
            finally{picker.hide();}
        });
    }
    @Test void removingLastAlternativeDoesNotReinsertItOnOk()throws Exception {
        FxLayoutTestSupport.run(()->{
            javafx.application.Platform.setImplicitExit(false);
            final var picker=new IngredientPicker(null,FxLayoutTestSupport.discovery(),dev.recipeeditor.registry.ResourceKind.ITEM,true,false,"[{\"item\":\"test:a\"}]");
            try{picker.show();final var remove=picker.getDialogPane().lookupAll(".button").stream().filter(node->node instanceof javafx.scene.control.Button button&&button.getText().equals("Remove selected alternative")).map(node->(javafx.scene.control.Button)node).findFirst().orElseThrow();remove.fire();((javafx.scene.control.Button)picker.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK)).fire();assertNull(picker.getResult());assertTrue(picker.isShowing());}
            finally{picker.hide();}
        });
    }

}
