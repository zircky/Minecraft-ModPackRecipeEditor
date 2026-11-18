package dev.recipeeditor.recipeanalyzer.conflict;

import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeanalyzer.model.*;
import dev.recipeeditor.recipeengine.*;
import java.util.*;

/**
 * Full-slot bipartite matching for unordered Vanilla inputs; shaped matching preserves holes and
 * mirror rules.
 */
public final class IngredientOverlap {
  private final AnalysisTagResolver tags;
  private boolean uncertain;

  public IngredientOverlap(final AnalysisTagResolver tags) {
    this.tags = tags;
  }

  public record Match(boolean overlaps, boolean uncertain) {}

  public Set<NodeKey> candidates(final RecipeFacts recipe) {
    final var result = new LinkedHashSet<NodeKey>();
    for (final var port : recipe.inputs()) collect(port.ingredient(), result);
    return result;
  }

  private void collect(final DependencyIngredient value, final Set<NodeKey> result) {
    if (value.resource() == null) {
      value.alternatives().forEach(v -> collect(v, result));
      return;
    }
    if (value.resource().type() == DependencyNodeType.TAG) {
      result.add(value.resource());
      result.addAll(tags.resolve(value.resource()).members());
    } else if (value.resource().type() != DependencyNodeType.UNKNOWN) result.add(value.resource());
  }

  public Match match(final RecipeFacts a, final RecipeFacts b) {
    uncertain = false;
    if (!a.type().equals(b.type())) return new Match(false, false);
    if (a.type().equals("minecraft:crafting_shaped"))
      return new Match(shaped(a.definition(), b.definition()), uncertain);
    if (a.inputs().size() != b.inputs().size()) return new Match(false, false);
    final int[] assigned = new int[b.inputs().size()];
    Arrays.fill(assigned, -1);
    for (int i = 0; i < a.inputs().size(); i++)
      if (!assign(i, a.inputs(), b.inputs(), assigned, new boolean[assigned.length]))
        return new Match(false, uncertain);
    return new Match(!a.inputs().isEmpty(), uncertain);
  }

  private boolean assign(
      final int index,
      final List<RecipePort> a,
      final List<RecipePort> b,
      final int[] assigned,
      final boolean[] visited) {
    for (int j = 0; j < b.size(); j++)
      if (!visited[j] && overlap(a.get(index).ingredient(), b.get(j).ingredient())) {
        visited[j] = true;
        if (assigned[j] < 0 || assign(assigned[j], a, b, assigned, visited)) {
          assigned[j] = index;
          return true;
        }
      }
    return false;
  }

  private boolean overlap(final DependencyIngredient a, final DependencyIngredient b) {
    if (a.resource() == null) {
      for (final var choice : a.alternatives()) if (overlap(choice, b)) return true;
      return false;
    }
    if (b.resource() == null) {
      for (final var choice : b.alternatives()) if (overlap(a, choice)) return true;
      return false;
    }
    if (a.resource().type() == DependencyNodeType.UNKNOWN
        || b.resource().type() == DependencyNodeType.UNKNOWN) {
      uncertain = true;
      return true;
    }
    if (a.resource().type() == DependencyNodeType.TAG
        || b.resource().type() == DependencyNodeType.TAG) {
      final var left = members(a.resource());
      final var right = members(b.resource());
      if (left.isEmpty() || right.isEmpty()) {
        uncertain = true;
        return true;
      }
      for (final var value : left) if (right.contains(value)) return true;
      return false;
    }
    return a.resource().equals(b.resource());
  }

  private Set<NodeKey> members(final NodeKey key) {
    if (key.type() != DependencyNodeType.TAG) return Set.of(key);
    final var resolved = tags.resolve(key);
    if (resolved.status() != TagResolution.Status.RESOLVED_RUNTIME || resolved.truncated())
      uncertain = true;
    return Set.copyOf(resolved.members());
  }

  private List<List<DependencyIngredient>> grid(final RecipeDefinition recipe) {
    final var pattern = recipe.layout().pattern();
    if (pattern.isEmpty()) return List.of();
    int left = pattern.getFirst().length(), right = 0, top = 0, bottom = pattern.size();
    for (final var row : pattern)
      for (int x = 0; x < row.length(); x++)
        if (row.charAt(x) != ' ') {
          left = Math.min(left, x);
          right = Math.max(right, x + 1);
        }
    while (top < bottom && pattern.get(top).isBlank()) top++;
    while (bottom > top && pattern.get(bottom - 1).isBlank()) bottom--;
    final var result = new ArrayList<List<DependencyIngredient>>();
    final var mapper = new IngredientDependencies();
    for (int y = top; y < bottom; y++) {
      final var row = new ArrayList<DependencyIngredient>();
      for (int x = left; x < right; x++) {
        final char key = pattern.get(y).charAt(x);
        row.add(
            key == ' '
                ? null
                : mapper.describe(recipe.layout().slots().get(String.valueOf(key)), true));
      }
      result.add(row);
    }
    return result;
  }

  private boolean shaped(final RecipeDefinition a, final RecipeDefinition b) {
    final var left = grid(a);
    final var right = grid(b);
    if (left.isEmpty()
        || left.size() != right.size()
        || left.getFirst().size() != right.getFirst().size()) return false;
    for (final boolean mirror : new boolean[] {false, true}) {
      boolean match = true;
      for (int y = 0; y < left.size(); y++)
        for (int x = 0; x < left.get(y).size(); x++) {
          final var aa = left.get(y).get(x);
          final var bb = right.get(y).get(mirror ? right.get(y).size() - x - 1 : x);
          if (aa == null || bb == null) {
            if (aa != bb) match = false;
          } else if (!overlap(aa, bb)) match = false;
        }
      if (match) return true;
    }
    return false;
  }
}
