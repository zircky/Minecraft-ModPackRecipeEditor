package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;

/** Card selection belongs to the resource identity, never to a recycled row or page position. */
public final class ResourceCardView extends Button {
    private final ChangeListener<ResourceLocation> selectionListener;
    public ResourceCardView(final ResourceLocation id,final ReadOnlyObjectProperty<ResourceLocation> selection) {
        getStyleClass().add("resource-card");setUserData(id);
        selectionListener=(o,a,b)->pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"),id.equals(b));
        selection.addListener(new WeakChangeListener<>(selectionListener));
        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"),id.equals(selection.get()));
    }
}
