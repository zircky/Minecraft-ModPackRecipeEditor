# Phase 14 — Technical audit (Step 2)

Date: 21 November 2026. Version: **1.1.0-alpha.1**, unchanged.
Scope: **Step 2 Technical Audit and Step 3 UI Consistency only**.

## Git preparation

Base: `master` / `origin/master`, `6c02e3f`, merge of Phase 13 PR #2.
Phase 12 PR #1 and Phase 13 are already included. Phase 11 is in the earlier
`d83d347` history. No separate Phase 11 branch is present; deleted remote Phase
12/13 branches do not imply missing changes. No unpushed base commits, conflicts
or tracked local edits were found. Existing untracked Phase 13 logs are preserved.
Working branch: `chore/phase-14-ui-polish-regression-testing`.

## Baseline verification

`gradlew.bat build`: **BUILD SUCCESSFUL**, 17s (69 tasks, mostly up-to-date).
Existing unit/integration results: 349 tests, 347 passed, 2 Windows symlink skips,
zero failures/errors. Up-to-date results are not presented as newly rerun tests.
Architecture checks are part of build; no production dependency cycles reported.
JavaFX starts on this Windows 11 host with version 1.1.0-alpha.1. The existing
unnamed-module JavaFX configuration warning is present; no startup exception.
The shell and About dialog were inspected in the real graphical session.
About reports protocol 1, Companion 0.1.0, project format 1 and NOT READY.

## Component audit

Statuses distinguish implementation from verification. Existing automated tests
cover bounded components, not a complete manual end-to-end acceptance campaign.

| Component | Implemented | Tested at baseline | Issues | Status |
|---|---|---|---|---|
| Recipe Viewer | Static text/ID viewer, source/JSON tabs | Existing parser/index tests; code review | Browser still uses text ingredient cards, not the new image slots; JEI/EMI viewer remains Step 4 | PARTIALLY_IMPLEMENTED |
| Recipe Editor | Visual Vanilla layouts, Raw/Advanced/Diff, draft history, validation | Existing JavaFX shaped/shapeless/slot integration tests and domain tests | Separate dialog; full Visual/Raw/save/reopen GUI campaign not run | PARTIALLY_IMPLEMENTED |
| Recipe Creator | Type/ID prompts reuse RecipeEditorDialog and ChangeSet | Existing form/editor/project tests | Multi-dialog flow, not embedded workspace; full manual creation not verified | PARTIALLY_IMPLEMENTED |
| Ingredient Picker | Bounded visual pages, names/previews, IDs/tags/alternatives | Existing registry/model tests; code review | Full fluid/block/tag selection GUI matrix not verified | PARTIALLY_IMPLEMENTED |
| Resource Browser | Items/Blocks/Fluids, bounded virtualized pages, details and source filters | Existing model/texture/localization tests | Empty filtered pages lack actionable explanation; filter history resets when switching categories | PARTIALLY_IMPLEMENTED |
| GregTech Materials | Snapshot-backed materials and registered forms | Existing material protocol/collector/model tests | Dedicated material color/icon-set metadata absent; no new live Minecraft collection | PARTIALLY_IMPLEMENTED |
| KubeJS Export | Nine Vanilla serializers, modes, preview, guarded writes/manifests | Existing exporter and cross-module tests; Phase 13 real-pack probe recorded separately | No new live reload acceptance; GT8/Create/Mekanism export unsupported | IMPLEMENTED (checked Vanilla subset) |
| Provenance | Namespace/container/source separated; all candidates retained; runtime evidence | Existing resolver/merge/project/export regressions | Static active winner and declaring author remain UNKNOWN by design | IMPLEMENTED |
| Runtime Companion | Read-only authenticated sync, snapshots, resource/material datasets | Existing protocol, client/server integration tests | No Minecraft launch during this audit; live end-to-end acceptance NOT_VERIFIED | PARTIALLY_IMPLEMENTED |
| Analyzer | Backend graph/conflict analysis APIs | Existing analyzer tests | MainWindow never installs an Analyzer workspace; navigation text incorrectly suggests recipe loading is absent | NOT_IMPLEMENTED (workspace) |
| Settings / dashboard | Informational empty states | Initial shell inspected | Full settings and dashboard absent | NOT_IMPLEMENTED |
| Directory history | Import remembers last existing folder/nearest parent | Code review | Open/Save/Export/snapshot history not unified; deferred Step 5 / navigation work | PARTIALLY_IMPLEMENTED |
| UI consistency | One CSS file, some shared slots | Baseline shell/About observed | Nested toolbar padding, mixed view insets, one inline GT card style, generic empty tables, no shared notification component | PARTIALLY_IMPLEMENTED |
| Workspace state | In-memory view objects, limited import preference | Code review | No geometry/theme/language/recent-project persistence; Recipes navigation resets filters | NOT_IMPLEMENTED (full persistence) |

No confirmed data-loss defect was reproduced in this bounded audit. This is not
proof that all data-loss or shutdown scenarios are safe. No production TODO/FIXME
markers were found in the scanned Java sources. Stale package-info comments in
modpack-parser/recipe-analyzer still describe these implemented modules as reserved
boundaries; documentation markers are not evidence of missing implementations.

## Step 3 changes selected

- Consolidate palette, spacing, typography and control states in theme.css.
- Remove compounded shell toolbar padding; permit toolbar overflow and wrap filter rows.
- Share explanatory empty states with real existing actions; keep unavailable
  Settings/Analyzer functions explicitly unavailable rather than adding fake buttons.
- Add a single dismissible in-app notification banner for coarse project outcomes.
- Share explicit theme installation across scenes/dialogs, including ownerless dialogs.
- Replace the remaining inline GregTech card style with a CSS class.
- Verify CSS and empty states in actual JavaFX integration tests and visually inspect
  the final shell, Runtime, Settings/Analyzer and About. Do not claim all screens
  or Windows display-scale combinations were manually exercised.

## Deferred work and release recommendation

Steps 4–8 require a separate request: recipe viewer/workflow redesign, navigation
and chooser history, cross-module/live Minecraft acceptance, profiling and packaging
release validation. No optimization is justified without measurements. No memory
leak assessment was performed. No Windows 10, multi-monitor, 125%/150% system DPI,
access-denied or long-path platform campaign was run. Packaging tasks exist but
current installers have not been validated by this audit.

Recommendation: **continue alpha**. This iteration does not establish Phase 14
completion or beta/RC readiness. Historical release reports remain historical;
in particular the old absence of Git/remote in RELEASE_READINESS.md is no longer
current, while its unverified platform/product gates still require review.
