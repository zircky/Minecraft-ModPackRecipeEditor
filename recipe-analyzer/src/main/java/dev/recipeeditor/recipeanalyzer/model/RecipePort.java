package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeanalyzer.graph.DependencyEdge.*;

public record RecipePort(
    String slot,
    DependencyIngredient ingredient,
    Consumption consumption,
    Cadence cadence,
    Integer chance,
    Integer maxChance,
    Integer boost) {}
