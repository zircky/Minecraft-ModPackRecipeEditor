package dev.recipeeditor.app.release;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class AboutDialogLayoutTest {
    @Test void completeBuildIdentityFitsTheThemedDialogWithoutClipping() throws Exception {
        try { Platform.startup(()->{}); } catch(IllegalStateException alreadyStarted) { }
        final var check=new FutureTask<Void>(()->{
            Platform.setImplicitExit(false);
            final var owner=new Stage();
            owner.setScene(new Scene(new VBox(),600,400));owner.show();
            final var metadata=BuildMetadata.load();
            final var dialog=new ReleaseInformationView(metadata,path->{}).aboutDialog(owner);
            try {
                dialog.show();
                final var pane=dialog.getDialogPane();pane.applyCss();pane.layout();
                final var content=(Label)pane.getContent();
                assertEquals(metadata.diagnosticSummary(),content.getText());
                assertTrue(content.getText().endsWith("Release: NOT READY"));
                assertTrue(content.getHeight()>=content.prefHeight(content.getWidth())-1,"All metadata lines must fit");
                assertTrue(content.getBoundsInParent().getMaxY()<=pane.getHeight(),"Content must stay inside the dialog");
            } finally { dialog.close();owner.close(); }
            return null;
        });
        Platform.runLater(check);check.get(15,TimeUnit.SECONDS);
    }
}
