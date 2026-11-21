package dev.recipeeditor.ui;

import dev.recipeeditor.registry.*;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class ComboBoxThemeTest {
    @Test void closedDisplayAndPopupKeepIndependentSelectionPaint()throws Exception {
        FxLayoutTestSupport.run(()->{
            Platform.setImplicitExit(false);
            final var combo=new ComboBox<String>();combo.getItems().setAll("NAMESPACE","All","en_us");combo.getSelectionModel().selectFirst();
            final var root=new VBox(combo,new Button("Other focus target"));final var stage=show(root);
            try {
                checkClosed(combo,root);
                pseudo(combo,"hover",true);root.applyCss();assertEquals(Color.web("#35383f"),paint(combo));
                pseudo(combo,"focused",true);root.applyCss();assertEquals(1,combo.getBackground().getFills().size());assertEquals(1,combo.getBorder().getStrokes().getFirst().getWidths().getTop());assertEquals(Color.web("#737780"),combo.getBorder().getStrokes().getFirst().getTopStroke());
                pseudo(combo,"hover",false);pseudo(combo,"focused",false);root.applyCss();assertEquals(Color.web("#2b2d30"),paint(combo));
                combo.show();root.applyCss();
                final var popup=Window.getWindows().stream().filter(window->window instanceof PopupWindow p&&p.getOwnerWindow()==stage).findFirst().orElseThrow();
                final var list=(ListView<?>)popup.getScene().getRoot().lookup(".list-view");assertNotNull(list);list.applyCss();list.layout();
                final var selected=list.lookupAll(".list-cell").stream().filter(node->node instanceof ListCell<?> cell&&cell.isSelected()).map(node->(ListCell<?>)node).findFirst().orElseThrow();
                assertEquals(Color.web("#484b52"),paint(selected));
                pseudo(selected,"hover",true);list.applyCss();assertEquals(Color.web("#535760"),paint(selected));pseudo(selected,"hover",false);
                list.getSelectionModel().select(1);assertEquals("All",combo.getValue());combo.hide();root.applyCss();assertEquals(Color.TRANSPARENT,paint((Region)combo.lookup(".list-cell")));
                combo.setDisable(true);root.applyCss();assertEquals(0.5,combo.getOpacity());assertEquals(Color.web("#25262a"),paint(combo));
            }finally{combo.hide();stage.close();}
        });
    }
    @ParameterizedTest @EnumSource(ResourceKind.class)
    void itemsBlocksAndFluidsUseTheSharedTheme(final ResourceKind kind)throws Exception {
        FxLayoutTestSupport.run(()->{try(final var textures=new TextureResourceService()) {
            final var view=new ResourceBrowserView(FxLayoutTestSupport.discovery(),textures,query->{},(entry,uses)->false);view.show(kind);
            final var stage=show(view);try{checkAll(view);}finally{view.deactivate();stage.close();}
        }});
    }
    @Test void materialsAndRecipesUseTheSharedTheme()throws Exception {
        FxLayoutTestSupport.run(()->{try(final var textures=new TextureResourceService();final var tasks=new EditorTasks()) {
            final var discovery=FxLayoutTestSupport.discovery();final var materials=new MaterialsBrowserView(discovery,textures,form->{});
            final var materialStage=show(materials);try{checkAll(materials);}finally{materials.deactivate();materialStage.close();}
            final var validator=new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),discovery.registry());
            try(final var recipes=new RecipeBrowserView(discovery,new dev.recipeeditor.recipeengine.editor.RecipeEditorService(discovery.recipes(),validator),tasks,(recipe,format)->{},new dev.recipeeditor.integrations.api.IntegrationContext("1.20.1",dev.recipeeditor.core.importing.ModLoaderType.FORGE,Map.of(),Map.of()))) {
                final var recipeStage=show(recipes);try{checkAll(recipes);}finally{recipeStage.close();}
            }
        }});
    }
    @Test void ownerlessIngredientPickerAlsoThemesDisabledComboBoxes()throws Exception {
        FxLayoutTestSupport.run(()->{Platform.setImplicitExit(false);final var picker=new IngredientPicker(null,FxLayoutTestSupport.discovery(),ResourceKind.ITEM,false,false);
            try{picker.show();picker.getDialogPane().applyCss();picker.getDialogPane().layout();checkAll(picker.getDialogPane());assertTrue(picker.getDialogPane().lookupAll(".combo-box").stream().anyMatch(Node::isDisabled));}
            finally{picker.hide();}
        });
    }
    @Test void exportWizardUsesTheSharedThemeWithoutWritingFiles()throws Exception {
        FxLayoutTestSupport.run(()->{
            Platform.setImplicitExit(false);
            final var root=java.nio.file.Path.of(".").toAbsolutePath().normalize();
            final var pack=new dev.recipeeditor.core.importing.ModpackDefinition(root,root,"Theme inspection",new dev.recipeeditor.core.importing.MinecraftVersion("1.20.1",List.of()),new dev.recipeeditor.core.importing.ModLoaderDefinition(dev.recipeeditor.core.importing.ModLoaderType.FORGE,"47.x",List.of()),List.of());
            final var discovery=FxLayoutTestSupport.discovery();
            final var validator=new dev.recipeeditor.recipeengine.editor.RecipeValidator(new dev.recipeeditor.integrations.ProjectRecipeParsers().parsers(),discovery.registry());
            final var planner=new dev.recipeeditor.exporters.ExportPlanner(List.of(),new dev.recipeeditor.exporters.ExportValidator(validator));
            try(final var service=new dev.recipeeditor.exporters.RecipeExportService(planner)) {
                final var wizard=new ExportWizard(null,service,pack,discovery.recipes(),new dev.recipeeditor.recipeengine.editor.RecipeChangeSet(Map.of()),root.resolve("theme-inspection.json"),Set.of(),new dev.recipeeditor.exporters.ExportWorkspace(),Set.of(),dev.recipeeditor.exporters.ExportFormat.AUTO,List.of(),path->{});
                wizard.show();final var window=Window.getWindows().stream().filter(value->value instanceof Stage stage&&stage.getTitle().startsWith("Recipe Export Wizard")).findFirst().orElseThrow();
                try{window.getScene().getRoot().applyCss();window.getScene().getRoot().layout();checkAll(window.getScene().getRoot());}
                finally{window.hide();}
            }
        });
    }
    private Stage show(final Parent root){Platform.setImplicitExit(false);final var stage=new Stage();final var scene=new Scene(root,1100,800);new UiTheme().apply(scene);stage.setScene(scene);stage.show();root.applyCss();root.layout();return stage;}
    private void checkAll(final Parent root){final var combos=root.lookupAll(".combo-box");assertFalse(combos.isEmpty());for(final var node:combos)checkClosed((ComboBox<?>)node,root);}
    private void checkClosed(final ComboBox<?> combo,final Parent root) {
        final var cell=(ListCell<?>)combo.lookup(".list-cell");assertNotNull(cell);assertEquals(Color.TRANSPARENT,paint(cell),"Combo "+combo.getStyleClass()+" cell parent "+cell.getParent().getStyleClass());
        // Guard the exact selector collision, including skin-created selected/odd display cells.
        cell.updateSelected(true);root.applyCss();assertEquals(Color.TRANSPARENT,paint(cell),"Combo "+combo.getStyleClass()+" cell parent "+cell.getParent().getStyleClass());cell.updateSelected(false);
        if(!combo.isDisabled()&&!combo.getItems().isEmpty()){combo.getSelectionModel().selectLast();root.applyCss();assertEquals(combo.getItems().getLast(),combo.getValue());assertEquals(Color.TRANSPARENT,paint(cell),"Combo "+combo.getStyleClass()+" cell parent "+cell.getParent().getStyleClass());}
    }
    private void pseudo(final Node node,final String state,final boolean active){node.pseudoClassStateChanged(PseudoClass.getPseudoClass(state),active);}
    private javafx.scene.paint.Paint paint(final Region node){return node.getBackground().getFills().getFirst().getFill();}
}
