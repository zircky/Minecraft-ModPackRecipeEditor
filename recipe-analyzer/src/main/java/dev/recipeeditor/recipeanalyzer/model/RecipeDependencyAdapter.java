package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeengine.RecipeDefinition;

/**
 * Explicit adapters may expose chemical/material operands; no namespace/name guessing is performed.
 */
public interface RecipeDependencyAdapter {
  boolean supports(RecipeDefinition recipe);

  ListPorts describe(RecipeDefinition recipe);

  record ListPorts(
      java.util.List<RecipePort> inputs,
      java.util.List<RecipePort> outputs,
      boolean complete,
      java.util.List<String> uncertainties) {
    public ListPorts {
      inputs = java.util.List.copyOf(inputs);
      outputs = java.util.List.copyOf(outputs);
      uncertainties = java.util.List.copyOf(uncertainties);
    }
  }
}
