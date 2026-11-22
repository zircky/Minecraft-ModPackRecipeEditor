package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.ResourceIdentity;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;

/** Selection uses both registry kind and ID, including equal Item and Block IDs. */
public final class ResourceCardView extends Button {
    private final InvalidationListener selectionListener;
    public ResourceCardView(final ResourceLocation id,final ReadOnlyObjectProperty<ResourceLocation> selection){this(id,id,selection);}
    public ResourceCardView(final ResourceIdentity identity,final ReadOnlyObjectProperty<ResourceIdentity> selection){this(identity,identity.id(),selection);}
    private ResourceCardView(final Object key,final ResourceLocation id,final ReadOnlyObjectProperty<?> selection){
        getStyleClass().add("resource-card");setUserData(id);
        selectionListener=observable->pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"),key.equals(selection.get()));
        selection.addListener(new WeakInvalidationListener(selectionListener));selectionListener.invalidated(selection);
    }
}
