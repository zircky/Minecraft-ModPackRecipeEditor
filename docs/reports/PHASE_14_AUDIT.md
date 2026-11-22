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

## Step 3 — Implemented UI consistency changes

- `UiTheme` installs the same stylesheet on scenes and dialog panes, including
  ownerless alerts. Explicit themes now cover editor, picker, export, creation,
  confirmations, errors, runtime snapshot and About presentation.
- `theme.css` defines shared JavaFX looked-up colors, 16px workspace insets,
  header/filter/detail classes, typography, focus/disabled states, tables, tabs,
  tooltips and notifications. Palette changes no longer require controller edits.
  The remaining inline GT property-card style was moved to CSS.
- The shell no longer nests two toolbars inside another toolbar. Primary controls
  use the available width, with the existing ToolBar overflow behavior. Filter
  and Runtime/export action rows wrap using FlowPane.
- `EmptyStateView` is shared by shell sections, mod/recipe tables, resource and
  material pages, Runtime registry and recipe selection. Import and Open Runtime
  buttons call existing actions; Settings/Analyzer do not offer dummy actions.
  Analyzer text no longer falsely claims recipe loading is unimplemented.
- `NotificationBanner` displays one dismissible SUCCESS/INFO/WARNING/ERROR outcome.
  It is wired to indexing/import outcomes, project save, recipe save and export
  outcomes. Messages distinguish project memory from disk and generated export
  files from Minecraft validation. Existing validation/diagnostic views remain.
- Manual inspection caught About text clipping after the padding change. The
  dialog now uses a wrapping Label with its preferred minimum height and a
  resizable 540px layout. `AboutDialogLayoutTest` opens the actual themed dialog
  and checks that all metadata lines fit, including `Release: NOT READY`.

No recipe data model, import/export generation rules, Companion code, version,
project/protocol formats or source JARs were changed. Filters' matching rules and
project persistence remain the existing implementations. Reset actions on empty
screens only clear presentation filters.

### Changed files

- `desktop-ui/src/main/resources/dev/recipeeditor/ui/theme.css`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/UiTheme.java` (new)
- `desktop-ui/src/main/java/dev/recipeeditor/ui/EmptyStateView.java` (new)
- `desktop-ui/src/main/java/dev/recipeeditor/ui/NotificationBanner.java` (new)
- `desktop-ui/src/main/java/dev/recipeeditor/ui/MainWindow.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/NavigationSection.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ModCatalogView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/MaterialsBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RuntimeIntegrationView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeEditorDialog.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/IngredientPicker.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ExportWizard.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/GregTechRecipeEditorProvider.java`
- `app/src/main/java/dev/recipeeditor/app/release/ReleaseInformationView.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/UiConsistencyTest.java` (new)
- `app/src/test/java/dev/recipeeditor/app/release/AboutDialogLayoutTest.java` (new)
- `PHASE_14_AUDIT.md` (new)

The CSS uses JavaFX property lookup, not browser CSS variables; reference:
[JavaFX 21 CSS Reference](https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/doc-files/cssref.html).

## Final validation of Steps 2–3

| Check | Actual result |
|---|---|
| Final `gradlew.bat clean build` | BUILD SUCCESSFUL, 2m 20s, 82 executed tasks |
| Separate `gradlew.bat test` | BUILD SUCCESSFUL, 2s, tasks up-to-date after clean build |
| Unit/component suite | 311 tests: 309 passed, 2 Windows symlink skips, zero failures/errors |
| Integration suite | 45 passed, zero skips/failures/errors |
| JavaFX tests within integration | 10 passed: 9 desktop-ui cases and 1 actual About-dialog layout case |
| New Step 3 regression cases | 7: empty-state action, banner replacement/dismissal, dialog palette/control metrics, three logical window widths, About text bounds |
| `gradlew.bat :app:run` | Final application started with desktop 1.1.0-alpha.1 / protocol 1 / Companion 0.1.0 / project format 1 |
| Manual graphical smoke | Projects empty screen, Materials missing-runtime state and working Open Runtime action, disconnected Runtime screen, Analyzer/Settings unavailable states, final About with every metadata line visible |
| CSS/startup diagnostics | No new CSS or startup exceptions observed; existing unnamed-module JavaFX warning remains |
| `git diff --check` | Passed |
| Live Minecraft / export reload | NOT_VERIFIED in this iteration |
| Profiling / memory / throughput | NOT_VERIFIED; no performance claims |
| Windows packaging / installation | NOT_VERIFIED in this iteration; deferred Step 8 |
| Windows 10 / other system DPI settings | NOT_VERIFIED |

The three JavaFX width cases are scene-graph tests at 1280, 1920 and 2560 logical
pixels; they are not full-screen acceptance tests or OS-DPI tests. Manual inspection
used the actual 1322x852 captured desktop window. Attempted border dragging did not
change its dimensions, so a manual 1280x720 resize is not claimed. UI tests ran in
this real Windows graphical environment; no TestFX or other dependency was added.

Local evidence: `.manual-tests/phase14-audit-build.log`, `phase14-build.log`,
`phase14-test.log`, `phase14-ui-tests.log`, `phase14-about-test.log`,
`phase14-baseline-run.log`, `phase14-run.log`. Final XML/HTML results are in each
module's `build/test-results` / `build/reports/tests`; aggregate HTML is
`build/reports/tests/all/index.html`. Local logs are not included in source commits.

Step 2 audit commit: `f4b4262` (`docs(audit): assess phase 11-13 implementation and validation gaps`).
Step 3 is a separate `style(ui): unify application layouts and visual components`
commit, identifiable in this branch's Git history. Both steps are complete for
this iteration. Overall Phase 14 remains incomplete: Steps 4–8 are deferred.
Release recommendation remains **continue alpha / NOT READY for stable release**.
