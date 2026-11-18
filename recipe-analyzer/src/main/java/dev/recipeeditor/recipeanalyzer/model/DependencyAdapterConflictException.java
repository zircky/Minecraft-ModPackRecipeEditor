package dev.recipeeditor.recipeanalyzer.model;

/** Configuration conflict must not be downgraded to a malformed-recipe warning. */
public final class DependencyAdapterConflictException extends IllegalArgumentException {
  public DependencyAdapterConflictException(final String message) {
    super(message);
  }
}
