package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeengine.RecipeDefinition;
import java.util.*;

public record RecipeFacts(
    String key,
    RecipeDefinition definition,
    String origin,
    String sourceLabel,
    String canonicalHash,
    String inputSignature,
    String outputSignature,
    String ratioSignature,
    boolean confirmedMatcher,
    boolean knownDependencies,
    List<RecipePort> inputs,
    List<RecipePort> outputs,
    List<String> uncertainties) {
  public RecipeFacts {
    inputs = List.copyOf(inputs);
    outputs = List.copyOf(outputs);
    uncertainties = List.copyOf(uncertainties);
  }

  public String id() {
    return definition.id().toString();
  }

  public String type() {
    return definition.type().displayId();
  }
}
