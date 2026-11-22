package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import java.util.Objects;
/** Minecraft ItemTransform semantics: translation is clamped in model units after dividing by 16. */
public record GuiDisplayTransform(Vector rotation,Vector translation,Vector scale,Vector rightRotation) {
    public GuiDisplayTransform{Objects.requireNonNull(rotation);Objects.requireNonNull(translation);Objects.requireNonNull(scale);Objects.requireNonNull(rightRotation);}
    public static GuiDisplayTransform identity(){return new GuiDisplayTransform(new Vector(0,0,0),new Vector(0,0,0),new Vector(1,1,1),new Vector(0,0,0));}
}
