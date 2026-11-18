package dev.recipeeditor.recipeanalyzer.graph;

import java.util.Objects;

/**
 * domain separates item/fluid tags, gas/infuse/pigment/slurry, and machine families from physical
 * machines.
 */
public record NodeKey(DependencyNodeType type, String domain, String id) {
  public NodeKey {
    Objects.requireNonNull(type);
    if (domain == null || domain.isBlank() || id == null || id.isBlank())
      throw new IllegalArgumentException("Typed node identity required");
  }
}
