package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeanalyzer.graph.NodeKey;
import java.math.BigDecimal;
import java.util.*;

/** Recursive AND-slot/OR-choice operand. Unknown quantities are null, not fabricated defaults. */
public record DependencyIngredient(
    NodeKey resource,
    BigDecimal quantity,
    List<DependencyIngredient> alternatives,
    String representationHash) {
  public DependencyIngredient {
    alternatives = List.copyOf(alternatives);
    if ((resource == null) == alternatives.isEmpty())
      throw new IllegalArgumentException("Exactly one resource or nonempty alternatives required");
  }
}
