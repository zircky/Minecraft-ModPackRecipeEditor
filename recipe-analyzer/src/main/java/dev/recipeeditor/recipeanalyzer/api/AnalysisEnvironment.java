package dev.recipeeditor.recipeanalyzer.api;

import java.time.Instant;
import java.util.*;

public record AnalysisEnvironment(
    String minecraftVersion,
    String loader,
    Map<String, String> mods,
    Scope scope,
    Instant timestamp) {
  public enum Scope {
    STATIC_PROJECT,
    SNAPSHOT_PROJECT
  }

  public AnalysisEnvironment {
    Objects.requireNonNull(minecraftVersion);
    Objects.requireNonNull(loader);
    mods = Map.copyOf(mods);
    Objects.requireNonNull(scope);
    Objects.requireNonNull(timestamp);
  }
}
