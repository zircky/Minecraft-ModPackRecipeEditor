package dev.recipeeditor.recipeanalyzer.graph;

import java.math.BigDecimal;
import java.util.Objects;

public record DependencyEdge(
    NodeKey source,
    NodeKey target,
    String recipeKey,
    String slot,
    Role role,
    BigDecimal quantity,
    Consumption consumption,
    Cadence cadence,
    Integer chance,
    Integer maxChance,
    Integer boost,
    String representationHash) {
  public enum Role {
    INPUT,
    OUTPUT,
    ALTERNATIVE,
    TAG_MEMBER,
    MACHINE_REQUIREMENT
  }

  public enum Consumption {
    CONSUMED,
    CATALYST,
    PROBABILISTIC,
    UNKNOWN,
    NOT_APPLICABLE
  }

  public enum Cadence {
    PER_RECIPE,
    PER_TICK,
    UNKNOWN
  }

  public DependencyEdge {
    Objects.requireNonNull(source);
    Objects.requireNonNull(target);
    Objects.requireNonNull(role);
    Objects.requireNonNull(consumption);
    Objects.requireNonNull(cadence);
    if (quantity != null && quantity.signum() < 0)
      throw new IllegalArgumentException("Negative quantity");
  }

  public boolean guaranteedOutput() {
    return role == Role.OUTPUT
        && chance != null
        && maxChance != null
        && maxChance > 0
        && chance.equals(maxChance)
        && (boost == null || boost == 0);
  }
}
