package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
final class ResourceCardSelectionTest {
    @Test void selectionFollowsOnlyMatchingIdAndRecreatedCardsDoNotInheritOldRowSelection()throws Exception {
        FxLayoutTestSupport.run(()->{
            final var selected=new SimpleObjectProperty<ResourceLocation>();
            final var firstId=ResourceLocation.parse("minecraft:stone");final var secondId=ResourceLocation.parse("minecraft:tnt");
            final var first=new ResourceCardView(firstId,selected);final var second=new ResourceCardView(secondId,selected);
            final var pseudo=PseudoClass.getPseudoClass("selected");
            selected.set(firstId);assertTrue(first.getPseudoClassStates().contains(pseudo));assertFalse(second.getPseudoClassStates().contains(pseudo));
            selected.set(secondId);assertFalse(first.getPseudoClassStates().contains(pseudo));assertTrue(second.getPseudoClassStates().contains(pseudo));
            final var nextPageCard=new ResourceCardView(ResourceLocation.parse("minecraft:diamond"),selected);assertFalse(nextPageCard.getPseudoClassStates().contains(pseudo));
            final var returnedCard=new ResourceCardView(secondId,selected);assertTrue(returnedCard.getPseudoClassStates().contains(pseudo));
            selected.set(null);assertFalse(returnedCard.getPseudoClassStates().contains(pseudo));assertFalse(second.getPseudoClassStates().contains(pseudo));
        });
    }
}
