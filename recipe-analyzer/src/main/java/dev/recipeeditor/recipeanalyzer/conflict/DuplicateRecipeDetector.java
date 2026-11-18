package dev.recipeeditor.recipeanalyzer.conflict;

import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeanalyzer.model.*;
import java.util.*;
import java.util.function.Function;

public final class DuplicateRecipeDetector implements RecipeAnalyzer {
  @Override
  public String id() {
    return "duplicates";
  }

  @Override
  public AnalysisResult analyze(final AnalysisContext context) {
    final var out = new DiagnosticCollector(context.settings().maxDiagnostics());
    context.dataset().diagnostics().forEach(out::add);
    final var recipes = context.dataset().recipes();
    long exact = 0, ids = 0, equivalent = 0, similar = 0, runtimeGroups = 0;
    for (final var group : groups(recipes, RecipeFacts::canonicalHash).values())
      if (group.size() > 1) {
        context.checkCancelled();
        if (group.stream().anyMatch(r -> r.origin().equals("RUNTIME"))) {
          runtimeGroups++;
          out.add(
              diagnostic(
                  "SIMILAR_RECIPE",
                  "Identical normalized runtime representations; original definitions and callback"
                      + " state are unknown",
                  group,
                  context));
        } else {
          exact++;
          out.add(
              diagnostic(
                  "DUPLICATE_RECIPE",
                  "EXACT_DUPLICATE: identical complete source JSON representation;"
                      + " execution/provenance may still differ",
                  group,
                  context));
        }
      }
    for (final var group : groups(recipes, RecipeFacts::id).values())
      if (group.stream().map(RecipeFacts::canonicalHash).distinct().count() > 1) {
        ids++;
        out.add(
            diagnostic(
                group.stream().anyMatch(r -> r.origin().equals("RUNTIME"))
                    ? "RUNTIME_VARIANTS_SAME_ID"
                    : "RECIPE_ID_CONFLICT",
                "SAME_ID_COLLISION: different collected definitions share an ID; runtime winner is"
                    + " not inferred",
                group,
                context));
      }
    for (final var group :
        groups(
                recipes,
                r -> r.ratioSignature().isEmpty() ? "" : r.type() + ":" + r.ratioSignature())
            .values())
      if (group.size() > 1
          && group.stream().map(RecipeFacts::canonicalHash).distinct().count() > 1) {
        equivalent++;
        out.add(
            diagnostic(
                "EQUIVALENT_RECIPE",
                "Potential stoichiometric equivalence only; batching, layout/time and selection"
                    + " semantics are not proven equivalent",
                group,
                context));
      }
    for (final var group :
        groups(
                recipes,
                r ->
                    r.inputSignature().isEmpty() || r.outputSignature().isEmpty()
                        ? ""
                        : r.type() + ":" + r.inputSignature() + ":" + r.outputSignature())
            .values())
      if (group.size() > 1
          && group.stream().map(RecipeFacts::canonicalHash).distinct().count() > 1) {
        similar++;
        out.add(
            diagnostic(
                "SIMILAR_RECIPE",
                "Same declared IO signatures, differing properties; quantities/chances/conditions"
                    + " may affect behavior",
                group,
                context));
      }
    final long sameOutputs =
        groups(recipes, RecipeFacts::outputSignature).values().stream()
            .filter(g -> g.size() > 1)
            .count();
    return context.result(
        id(),
        !out.truncated(),
        Map.of(
            "recipes",
            (long) recipes.size(),
            "exactGroups",
            exact,
            "sameIdGroups",
            ids,
            "equivalentRatioGroups",
            equivalent,
            "similarGroups",
            similar,
            "sharedOutputGroups",
            sameOutputs,
            "runtimeRepresentationGroups",
            runtimeGroups),
        out.values());
  }

  private Map<String, List<RecipeFacts>> groups(
      final List<RecipeFacts> recipes, final Function<RecipeFacts, String> key) {
    final var groups = new LinkedHashMap<String, List<RecipeFacts>>();
    for (final var recipe : recipes) {
      final String value = key.apply(recipe);
      if (!value.isEmpty()) groups.computeIfAbsent(value, k -> new ArrayList<>()).add(recipe);
    }
    return groups;
  }

  private AnalysisDiagnostic diagnostic(
      final String code,
      final String message,
      final List<RecipeFacts> recipes,
      final AnalysisContext context) {
    return new AnalysisDiagnostic(
        code,
        code.equals("RECIPE_ID_CONFLICT")
            ? AnalysisDiagnostic.Severity.WARNING
            : AnalysisDiagnostic.Severity.INFO,
        message,
        recipes.stream().limit(context.settings().maxRelated()).map(RecipeFacts::id).toList(),
        List.of(),
        List.of(
            "Collected definitions: " + recipes.size(),
            "Sources: "
                + recipes.stream()
                    .limit(context.settings().maxRelated())
                    .map(RecipeFacts::sourceLabel)
                    .toList()),
        "Review complete definitions; matching outputs alone do not imply a duplicate or conflict");
  }
}
