package dev.recipeeditor.recipeanalyzer.conflict;

import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.graph.*;
import dev.recipeeditor.recipeanalyzer.model.*;
import java.util.*;

public final class RecipeConflictAnalyzer implements RecipeAnalyzer {
  private final List<RecipeConflictStrategy> strategies;

  public RecipeConflictAnalyzer() {
    this(List.of(new VanillaConflictStrategy(), new GregTechConflictStrategy()));
  }

  public RecipeConflictAnalyzer(final List<RecipeConflictStrategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  @Override
  public String id() {
    return "conflicts";
  }

  @Override
  public AnalysisResult analyze(final AnalysisContext context) {
    final var out = new DiagnosticCollector(context.settings().maxDiagnostics());
    context.dataset().diagnostics().forEach(out::add);
    final var ids = new LinkedHashMap<String, List<RecipeFacts>>();
    final var inputs = new LinkedHashMap<String, List<RecipeFacts>>();
    for (final var recipe : context.dataset().recipes()) {
      context.checkCancelled();
      ids.computeIfAbsent(recipe.id(), k -> new ArrayList<>()).add(recipe);
      if (!recipe.inputSignature().isEmpty())
        inputs
            .computeIfAbsent(recipe.type() + ":" + recipe.inputSignature(), k -> new ArrayList<>())
            .add(recipe);
    }
    if (context.usesSnapshot() || !context.changes().changes().isEmpty())
      originalEvidence(context, out);
    long sameIds = 0, confirmed = 0, potential = 0;
    for (final var group : ids.values())
      if (group.stream().map(RecipeFacts::canonicalHash).distinct().count() > 1) {
        sameIds++;
        out.add(
            new AnalysisDiagnostic(
                group.stream().anyMatch(r -> r.origin().equals("RUNTIME"))
                    ? "RUNTIME_VARIANTS_SAME_ID"
                    : "RECIPE_ID_CONFLICT",
                group.stream().anyMatch(r -> r.origin().equals("RUNTIME"))
                    ? AnalysisDiagnostic.Severity.INFO
                    : AnalysisDiagnostic.Severity.WARNING,
                "Different collected representations share an ID; runtime variants may be"
                    + " legitimate and their selection rules are not established",
                sample(group, context),
                List.of(),
                List.of("Definitions: " + group.size()),
                "Inspect sources, runtime variants and project quarantine"));
      }
    for (final var group : inputs.values())
      if (group.size() > 1
          && group.stream().map(RecipeFacts::canonicalHash).distinct().count() > 1) {
        final var assessment = assessment(group);
        if (assessment.confirmedOverlap()) confirmed++;
        else potential++;
        out.add(overlap(group, assessment, context));
      }
    // Distinct tag/alternative forms need bounded comparisons. Unknown mod selection rules stay
    // potential.
    final var matcher = new IngredientOverlap(new AnalysisTagResolver(context));
    final var candidates = new LinkedHashMap<NodeKey, List<RecipeFacts>>();
    int memberships = 0;
    boolean limited = false;
    candidateIndex:
    for (final var recipe : context.dataset().recipes())
      if (recipe.knownDependencies()
          && new VanillaConflictStrategy().supports(recipe.definition().type()))
        for (final var resource : matcher.candidates(recipe)) {
          if (++memberships > context.settings().maxComparisons()) {
            limited = true;
            break candidateIndex;
          }
          candidates.computeIfAbsent(resource, k -> new ArrayList<>()).add(recipe);
        }
    final var compared = new HashSet<Set<String>>();
    int comparisons = 0;
    outer:
    for (final var group : candidates.values())
      for (int i = 0; i < group.size(); i++)
        for (int j = i + 1; j < group.size(); j++) {
          context.checkCancelled();
          final var a = group.get(i);
          final var b = group.get(j);
          if (!a.type().equals(b.type()) || a.inputSignature().equals(b.inputSignature())) continue;
          if (!compared.add(Set.of(a.key(), b.key()))) continue;
          if (comparisons >= context.settings().maxComparisons()) {
            limited = true;
            break outer;
          }
          comparisons++;
          final var match = matcher.match(a, b);
          if (!match.overlaps()) continue;
          final var groupPair = List.of(a, b);
          final var rule = assessment(groupPair);
          final var result =
              new RecipeConflictStrategy.Assessment(
                  rule.confirmedOverlap() && !match.uncertain(),
                  rule.evidence()
                      + (match.uncertain()
                          ? "; unresolved/static tag evidence limits certainty"
                          : ""));
          if (result.confirmedOverlap()) confirmed++;
          else potential++;
          out.add(overlap(groupPair, result, context));
        }
    if (limited)
      out.add(
          new AnalysisDiagnostic(
              "CONFLICT_COMPARISON_LIMIT",
              AnalysisDiagnostic.Severity.WARNING,
              "Potential overlap search was bounded; it is not exhaustive",
              List.of(),
              List.of(),
              List.of("Comparisons: " + comparisons),
              "Narrow recipe types or raise comparison budget"));
    return context.result(
        id(),
        !limited && !out.truncated(),
        Map.of(
            "sameIdRepresentationGroups",
            sameIds,
            "confirmedMatcherOverlaps",
            confirmed,
            "potentialOverlaps",
            potential,
            "comparisons",
            (long) comparisons),
        out.values());
  }

  private void originalEvidence(final AnalysisContext context, final DiagnosticCollector out) {
    final var json = new CanonicalJson();
    for (final var group : context.originals().conflicts().values()) {
      context.checkCancelled();
      final var hashes = new HashSet<String>();
      for (final var recipe : group) {
        try {
          hashes.add(json.hash(json.canonical(recipe.rawJson())));
        } catch (IllegalArgumentException invalid) {
          hashes.add(json.hash(recipe.rawJson()));
        }
      }
      if (hashes.size() > 1)
        out.add(
            new AnalysisDiagnostic(
                "ORIGINAL_SOURCE_CONFLICT",
                AnalysisDiagnostic.Severity.INFO,
                "Original source definitions differ; effective runtime/project selection is"
                    + " analyzed separately",
                group.stream()
                    .limit(context.settings().maxRelated())
                    .map(r -> r.id().toString())
                    .toList(),
                List.of(),
                List.of("Original candidates: " + group.size()),
                "Review retained originals without confusing them with the effective layer"));
    }
  }

  private RecipeConflictStrategy.Assessment assessment(final List<RecipeFacts> recipes) {
    final var matching =
        strategies.stream()
            .filter(s -> s.supports(recipes.getFirst().definition().type()))
            .toList();
    if (matching.size() > 1) throw new IllegalArgumentException("Ambiguous conflict strategies");
    return matching.isEmpty()
        ? new RecipeConflictStrategy.Assessment(
            false,
            "No verified selection strategy for this type; overlapping declarations are not a"
                + " confirmed machine conflict")
        : matching.getFirst().assess(recipes);
  }

  private List<String> sample(final List<RecipeFacts> recipes, final AnalysisContext context) {
    return recipes.stream().limit(context.settings().maxRelated()).map(RecipeFacts::id).toList();
  }

  private AnalysisDiagnostic overlap(
      final List<RecipeFacts> recipes,
      final RecipeConflictStrategy.Assessment assessment,
      final AnalysisContext context) {
    return new AnalysisDiagnostic(
        assessment.confirmedOverlap() ? "CONFIRMED_MATCHER_OVERLAP" : "POTENTIAL_RECIPE_CONFLICT",
        AnalysisDiagnostic.Severity.WARNING,
        assessment.confirmedOverlap()
            ? "Confirmed overlapping matcher predicates, not a guaranteed invalid recipe"
            : "Potential ambiguity; selection behavior is unknown",
        sample(recipes, context),
        List.of(),
        List.of(assessment.evidence(), "Collected candidates: " + recipes.size()),
        "Review machine/type rules and runtime order; equal ingredients alone do not establish an"
            + " error");
  }
}
