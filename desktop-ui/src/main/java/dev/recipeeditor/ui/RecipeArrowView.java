package dev.recipeeditor.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;

/** Exactly one connector owned by a recipe layout, never by an output slot. */
public final class RecipeArrowView extends Label {
    public RecipeArrowView(){super("→");getStyleClass().add("recipe-arrow");setAlignment(Pos.CENTER);setMinWidth(64);setAccessibleText("Produces");}
}
