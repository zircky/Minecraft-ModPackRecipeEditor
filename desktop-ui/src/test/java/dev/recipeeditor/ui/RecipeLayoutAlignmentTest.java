package dev.recipeeditor.ui;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") final class RecipeLayoutAlignmentTest {
    @Test void allSupportedLayoutsKeepOneArrowAndEqualSlotSizesAtDifferentWidths()throws Exception {
        FxLayoutTestSupport.run(()->{try(final var textures=new dev.recipeeditor.registry.TextureResourceService()) {
            for(final var kind:java.util.Arrays.stream(RecipeLayoutProvider.Kind.values()).filter(value->value!=RecipeLayoutProvider.Kind.PROCESSING).toList())for(final int size:java.util.List.of(40,64,80))for(final int width:java.util.List.of(800,1280)) {
                final var inputs=new java.util.ArrayList<RecipeLayoutModel.Input>();for(int i=0;i<(kind==RecipeLayoutProvider.Kind.SHAPED||kind==RecipeLayoutProvider.Kind.SHAPELESS?9:kind.name().startsWith("SMITHING")?3:1);i++)inputs.add(new RecipeLayoutModel.Input(kind==RecipeLayoutProvider.Kind.SHAPED?"grid":kind==RecipeLayoutProvider.Kind.SHAPELESS?"shapeless":"role"+i,i,""));
                final var model=new RecipeLayoutModel("test:type",kind,inputs,RecipeSlotContent.parse(""),kind==RecipeLayoutProvider.Kind.SMITHING_TRIM,java.util.Map.of());
                final var view=new RecipeLayoutRenderer(model,FxLayoutTestSupport.discovery(),textures,size,"en_us",RecipeLayoutRenderer.Mode.VIEWER,null);new javafx.scene.Scene(view,width,500);view.applyCss();view.layout();
                assertEquals(1,view.lookupAll(".recipe-arrow").size());for(final var slot:view.lookupAll(".minecraft-recipe-slot")){assertEquals(size,((RecipeSlotView)slot).getWidth(),0.1);assertEquals(size,((RecipeSlotView)slot).getHeight(),0.1);}
            }
        }});
    }
}
