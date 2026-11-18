package dev.recipeeditor.recipeanalyzer.graph;

import java.util.*;

public record TagResolution(NodeKey tag, List<NodeKey> members, Status status, boolean truncated) {
  public enum Status {
    RESOLVED_RUNTIME,
    EMPTY_RUNTIME,
    MISSING_RUNTIME,
    INFERRED_STATIC,
    EMPTY_STATIC,
    MISSING_STATIC
  }

  public TagResolution {
    members = List.copyOf(members);
  }
}
