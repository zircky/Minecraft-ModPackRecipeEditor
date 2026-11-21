package dev.recipeeditor.ui;

import dev.recipeeditor.registry.ResourceKind;
import dev.recipeeditor.registry.TextureResourceService;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class UiConsistencyTest {
    @Test void emptyStateExplainsMissingDataAndOnlyOffersAnActualAction() throws Exception {
        FxLayoutTestSupport.run(()->{
            final var calls=new AtomicInteger();
            final var info=new EmptyStateView("Workspace unavailable","This workspace has not been implemented.");
            assertTrue(info.getChildren().stream().noneMatch(Button.class::isInstance));
            final var actionable=new EmptyStateView("No project","Import a Minecraft instance.","Import Modpack",calls::incrementAndGet);
            final var button=(Button)actionable.getChildren().stream().filter(Button.class::isInstance).findFirst().orElseThrow();
            assertEquals(0,calls.get());
            button.fire();
            assertEquals(1,calls.get());
        });
    }

    @Test void notificationsReplacePreviousOutcomeAndDismissWithoutLeavingLayoutSpace() throws Exception {
        FxLayoutTestSupport.run(()->{
            final var banner=new NotificationBanner();
            assertFalse(banner.isVisible());assertFalse(banner.isManaged());
            banner.show(NotificationBanner.Kind.ERROR,"Import failed");
            banner.show(NotificationBanner.Kind.SUCCESS,"Project saved");
            assertTrue(banner.isVisible());assertTrue(banner.isManaged());
            assertEquals(2,banner.getChildren().size());
            final var message=(Label)banner.getChildren().getFirst();
            assertTrue(message.getText().contains("Project saved"));assertFalse(message.getText().contains("Import failed"));
            ((Button)banner.getChildren().getLast()).fire();
            assertFalse(banner.isVisible());assertFalse(banner.isManaged());
        });
    }

    @Test void standaloneDialogUsesTheSamePaletteAndControlMetricsAsTheScene() throws Exception {
        FxLayoutTestSupport.run(()->{
            final var dialog=new DialogPane();
            final var button=new Button("Close");dialog.setContent(button);
            final var theme=new UiTheme();theme.apply(dialog);theme.apply(dialog);
            final var root=new VBox(dialog);final var scene=new Scene(root,600,400);
            theme.apply(scene);theme.apply(scene);root.applyCss();root.layout();
            assertEquals(1,scene.getStylesheets().size());assertEquals(1,dialog.getStylesheets().size());
            assertEquals(Color.web("#2b2d30"),dialog.getBackground().getFills().getFirst().getFill());
            assertEquals(7,button.getPadding().getTop(),0.1);
            assertEquals(12,button.getPadding().getLeft(),0.1);
            assertEquals(Color.web("#dfe1e5"),button.getTextFill());
        });
    }

    @ParameterizedTest @ValueSource(ints={1280,1920,2560})
    void emptyCataloguesRemainExplanatoryAcrossLogicalWindowSizes(final int width) throws Exception {
        FxLayoutTestSupport.run(()->{
            try(final var textures=new TextureResourceService()) {
                final var resources=new ResourceBrowserView(FxLayoutTestSupport.discovery(),textures,query->{},(entry,uses)->false);
                final var materials=new MaterialsBrowserView(FxLayoutTestSupport.discovery(),textures,form->{});
                final var root=new VBox(resources,materials);
                final var scene=new Scene(root,width,720);new UiTheme().apply(scene);
                resources.setPrefHeight(350);materials.setPrefHeight(350);
                resources.show(ResourceKind.ITEM);root.applyCss();root.layout();
                assertFalse(resources.lookupAll(".empty-state").isEmpty(),"Resources must explain an empty page");
                assertFalse(materials.lookupAll(".empty-state").isEmpty(),"Materials must explain missing runtime evidence");
                assertEquals(16,resources.getPadding().getLeft(),0.1);
                assertEquals(resources.getPadding(),materials.getPadding());
                resources.deactivate();materials.deactivate();
            }
        });
    }
}
