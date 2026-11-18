package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.integrations.api.SupportLevel;
import dev.recipeeditor.integrations.gregtech.*;
import dev.recipeeditor.recipeanalyzer.api.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.util.*;

/**
 * Reuses the editor's overlay/quarantine semantics. Runtime preference is explicit and time-scoped.
 */
public final class EffectiveAnalysisInput {
  public AnalysisDataset prepare(final AnalysisContext context) {
    final var diagnostics = new DiagnosticCollector(context.settings().maxDiagnostics());
    evidence(context, diagnostics);
    final var editor =
        new RecipeEditorService(
            context.originals(),
            new RecipeValidator(new ProjectRecipeParsers().parsers(), context.registry()));
    editor.restore(context.changes());
    for (final var conflict : editor.conflicts())
      diagnostics.add(
          new AnalysisDiagnostic(
              "PROJECT_CHANGE_CONFLICT",
              AnalysisDiagnostic.Severity.WARNING,
              "Project change was quarantined by the existing editor",
              List.of(conflict.recipeId().toString()),
              List.of(),
              List.of(conflict.message()),
              "Resolve original source references before applying the change"));
    final var selected = new LinkedHashMap<String, List<SourceRecipe>>();
    for (final var recipe : editor.effective().recipes())
      selected
          .computeIfAbsent(recipe.id().toString(), id -> new ArrayList<>())
          .add(
              new SourceRecipe(
                  recipe,
                  editor.snapshot().changes().containsKey(recipe.id()) ? "PROJECT" : "STATIC"));
    boolean runtimeLimited = false;
    if (context.usesSnapshot()) {
      final var runtime = new LinkedHashMap<String, List<SourceRecipe>>();
      final var bridge =
          new RuntimeDependencyBridge(
              context.registry(),
              context.environment().minecraftVersion().equals("1.20.1"),
              "7.1.4".equals(context.environment().mods().get("gtceu"))
                  && context.capabilities().containsKey("gtceu")
                  && context.capabilities().get("gtceu").compatibility() == SupportLevel.SUPPORTED);
      int runtimeCount = 0;
      runtimeLoop:
      for (final var dto : context.runtimeSnapshot().recipes()) {
        if (runtimeCount++ >= context.settings().maxRecipes()) {
          runtimeLimited = true;
          break;
        }
        context.checkCancelled();
        addRuntime(dto, runtime, bridge);
        final var variants = dto.properties().get("runtimeVariants");
        if (variants != null && variants.isJsonArray())
          for (final var variant : variants.getAsJsonArray())
            try {
              if (runtimeCount++ >= context.settings().maxRecipes()) {
                runtimeLimited = true;
                break runtimeLoop;
              }
              addRuntime(
                  ProtocolCodec.GSON.fromJson(variant, RuntimeData.Recipe.class), runtime, bridge);
            } catch (RuntimeException invalid) {
              if (invalid instanceof java.util.concurrent.CancellationException cancelled)
                throw cancelled;
              if (invalid instanceof DependencyAdapterConflictException conflict) throw conflict;
              diagnostics.add(
                  warning(
                      "UNSUPPORTED_RUNTIME_VARIANT",
                      dto.id(),
                      "A runtime variant cannot be mapped safely"));
            }
      }
      runtime.forEach(
          (id, recipes) -> {
            final var location = dev.recipeeditor.core.ResourceLocation.parse(id);
            if (!editor.snapshot().changes().containsKey(location)) selected.put(id, recipes);
          });
      // Active DELETE/UPDATE/CREATE always wins over the historical runtime state.
      for (final var change : editor.snapshot().changes().values())
        if (change.type() == RecipeChangeType.DELETE) selected.remove(change.id().toString());
    }
    final var adapters = new ArrayList<>(context.adapters());
    final var gt = context.capabilities().get("gtceu");
    if ("7.1.4".equals(context.environment().mods().get("gtceu"))
        && gt != null
        && gt.compatibility() == SupportLevel.SUPPORTED)
      adapters.add(new GregTechDependencyAdapter());
    final var mapper =
        new RecipeFactsMapper(
            new dev.recipeeditor.recipeanalyzer.graph.AnalysisTagResolver(context));
    final var facts = new ArrayList<RecipeFacts>();
    boolean limited = runtimeLimited;
    outer:
    for (final var recipes : selected.values())
      for (int ordinal = 0; ordinal < recipes.size(); ordinal++) {
        context.checkCancelled();
        if (facts.size() >= context.settings().maxRecipes()) {
          limited = true;
          break outer;
        }
        final var source = recipes.get(ordinal);
        try {
          final var mapped =
              mapper.map(
                  source.recipe(),
                  source.origin(),
                  ordinal,
                  adapters,
                  context.environment().minecraftVersion().equals("1.20.1"));
          facts.add(mapped);
        } catch (RuntimeException invalid) {
          if (invalid instanceof java.util.concurrent.CancellationException cancelled)
            throw cancelled;
          if (invalid instanceof DependencyAdapterConflictException conflict) throw conflict;
          diagnostics.add(
              warning(
                  "UNSUPPORTED_RECIPE_TYPE",
                  source.recipe().id().toString(),
                  "Dependency adapter unavailable; declared metadata retained"));
          facts.add(mapper.opaque(source.recipe(), source.origin(), ordinal));
        }
      }
    if (limited)
      diagnostics.add(
          warning("ANALYSIS_LIMIT", "", "Recipe budget reached; graph/diagnostics are incomplete"));
    final long uncertain = facts.stream().filter(f -> !f.knownDependencies()).count();
    if (uncertain > 0)
      diagnostics.add(
          new AnalysisDiagnostic(
              "UNSUPPORTED_RECIPE_TYPE",
              AnalysisDiagnostic.Severity.WARNING,
              "Some recipe semantics/dependencies are incomplete",
              facts.stream()
                  .filter(f -> !f.knownDependencies())
                  .limit(context.settings().maxRelated())
                  .map(RecipeFacts::id)
                  .toList(),
              List.of(),
              List.of("Recipes with uncertainty: " + uncertain),
              "Register a version-specific adapter; do not infer obtainability or costs"));
    return new AnalysisDataset(facts, diagnostics.values(), limited || diagnostics.truncated());
  }

  private record SourceRecipe(RecipeDefinition recipe, String origin) {}

  private void addRuntime(
      final RuntimeData.Recipe dto,
      final Map<String, List<SourceRecipe>> target,
      final RuntimeDependencyBridge bridge) {
    target
        .computeIfAbsent(dto.id(), id -> new ArrayList<>())
        .add(new SourceRecipe(bridge.map(dto), "RUNTIME"));
  }

  private void evidence(final AnalysisContext context, final DiagnosticCollector diagnostics) {
    if (context.runtimeSnapshot() == null)
      diagnostics.add(
          warning(
              "STATIC_INDEX_INCOMPLETE",
              "",
              "No runtime snapshot: programmatic recipes, loot, worldgen and other sources may be"
                  + " absent"));
    else {
      if (!context.snapshotCompatible())
        diagnostics.add(
            warning(
                "SNAPSHOT_ENVIRONMENT_MISMATCH",
                "",
                "Incomplete snapshot or Minecraft/loader/mod versions differ; runtime preference"
                    + " disabled"));
      try {
        final var time =
            java.time.Instant.parse(context.runtimeSnapshot().environment().collectedAt());
        if (time.plus(context.settings().snapshotMaxAge())
                .isBefore(context.environment().timestamp())
            || time.isAfter(context.environment().timestamp().plusSeconds(300)))
          diagnostics.add(
              warning(
                  "STALE_RUNTIME_SNAPSHOT",
                  "",
                  "Snapshot age/clock is outside the configured window"));
      } catch (java.time.DateTimeException invalid) {
        diagnostics.add(
            warning("STALE_RUNTIME_SNAPSHOT", "", "Snapshot collection time is invalid"));
      }
      diagnostics.add(
          warning(
              "SNAPSHOT_SCOPED_EVIDENCE",
              "",
              "Snapshot describes its captured environment/time; a live Minecraft generation was"
                  + " not verified by this offline analysis"));
    }
  }

  private AnalysisDiagnostic warning(final String code, final String id, final String message) {
    return new AnalysisDiagnostic(
        code,
        AnalysisDiagnostic.Severity.WARNING,
        message,
        id.isEmpty() ? List.of() : List.of(id),
        List.of(),
        List.of(),
        "Inspect provenance and missing adapters before drawing conclusions");
  }
}
