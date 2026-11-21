# Phase 14 — UI/UX fixes and resource rendering corrections

21 November 2026. Desktop **1.1.0-alpha.1**, Companion 0.1.0, protocol 1,
project format 1 — unchanged. This continues Phase 14, not a new phase.

Branch: `chore/phase-14-ui-polish-regression-testing`. Started at `e8853b1`;
the preceding work had already been merged into `origin/master` as `af817fd`.
The existing branch was retained, without another merge. Earlier untracked logs
were preserved. The supplied attachment contained the task text only; the five
referenced original screenshots were not attached. Diagnosis used the code,
reproduction and actual ISG resources rather than claiming a screenshot comparison.

## Causes and fixes

### Pagination

Resource Browser used the stock JavaFX Pagination skin under broad button styles,
without dedicated sizing or a direct page-number entry. Empty searches forced
one visible page with `Math.max(1, ...)`. The original clipping pixels could not
be measured without the missing screenshots.

`PaginationView` wraps the existing JavaFX page-index model; record slicing remains
in the browsers. It presents First/Previous/Next/Last, at most five numbered
buttons, ellipses, a page summary and direct numeric entry. Buttons have a 34px
minimum height and grow to fit their digits. Footer controls wrap on narrow layouts.
Zero results show no numbered pages and disable navigation. Index clamping also
works when filters shrink the page count; search resets the index. The component
is shared by resources, materials and Ingredient Picker.

### Vanilla resources and names

`ResourceScanner` previously read mod JARs, local KubeJS/data folders and datapacks,
but never the shared Minecraft client or launcher asset objects. Texture loading
also required every archive to be below the selected instance. Therefore an
available shared client JAR could not supply missing models/textures/translations.

`VanillaAssetLocator` now finds exact **local 1.20.1** artifacts in standard
`versions/1.20.1` layouts and PrismLauncher metadata/library layouts. For a Prism
instance it checks its launcher parent and the known `%APPDATA%/PrismLauncher`
directory; it does not enumerate Windows drives or arbitrary user directories.
It verifies metadata identity/version, canonical boundaries, file sizes and SHA-1
before using client/index/object files. No network download was performed or added.

Actual ISG sources found:

- Client: `C:\Users\ziran\AppData\Roaming\PrismLauncher\libraries\com\mojang\minecraft\1.20.1\minecraft-1.20.1-client.jar`.
- Client SHA-1 from verified local metadata: `0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838`.
- Asset index: `assets/indexes/5.json` in that launcher directory.
- Russian language object SHA-1: `0f501362172572eca985eecb8613ed2a1fe4d767`.
- English translations, models, textures and baseline tags come from the client;
  Russian translations come from the verified asset object.

`VANILLA_ASSET` sources retain the physical location and logical resource path.
TextureResourceService permits an external client only when rediscovered as an
approved exact artifact; arbitrary outside archives remain forbidden. Caches and
archive handles stay bounded. Resource packs in the selected instance are also
scanned as candidates. Conflicting definitions keep UNKNOWN load priority rather
than silently choosing an active pack. Static previews are available evidence,
not runtime registration or proof of the active source. Vanilla recipe JSON is
deliberately not imported by this asset-discovery change.

A second naming defect affected block items: the static index looked only for
`item.<namespace>.<path>`, while Minecraft and Ad Astra planks use existing
`block.<namespace>.<path>` translations. LocalizationService now uses the loaded
block key when the item key has no translation. No item-name or texture-ID table
was added. English/Russian/ID fallback remains generic and data-backed.

### Card labels and selection

The blue area was the selection of a **ListView row containing multiple cards**,
painted by the common `.list-cell:filled:selected` rule. The row selection model
is now disabled and row backgrounds scoped to transparent. `ResourceCardView`
tracks one Resource ID and paints only its own border/background. Recreated cells
derive state from identity; they never inherit a previous row index. Selecting the
same card again or changing filters clears selection.

ResourceBrowserViewModel supplies shared display names and tooltips. ID fallback
is displayed once on a card. Language changes rebuild only presentation, keep the
source filter and selected resource, and do not recreate RegistryIndex or import.
Ingredient Picker and recipe slots use the same registry, localization and preview
mechanisms. The static recipe viewer's shaped grid now reuses image slots as well.

### Crafting editor

The old editor used default 36px slots and drew an arrow both in RecipeEditorDialog
and in RecipeOutputEditor. The result also owned inline Choose/Remove buttons and
an independent texture service. The new layout uses:

- A square 3×3 grid, default **64px**, adjustable **40–80px**.
- One RecipeArrowView in the recipe layout, centered on the grid/output row.
- A selected-slot action row below the grid. Selecting alone does not edit JSON.
- Result selection by clicking its slot, with a context action for result/quantity.
  Mandatory Vanilla results cannot be accidentally removed by an inline button;
  explicit Raw JSON editing remains available and invalid results still block Save.
- Shared editor textures/registry for input and output previews.
- Grouped repeated diagnostics with their original severity and actual shaped-slot
  numbers; no warning is promoted to an error or silently omitted.
- Explicit tag identity and a `#` badge. A tag preview uses an indexed member;
  the tooltip identifies the representative member, not a fictitious single-item tag.

Visual/Raw/Diff, unknown JSON fields, draft Undo/Redo, project save and provenance
continue through the existing RecipeFormService / RecipeEditorService pipeline.

## Real resource verification

Read-only import: `G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh`.
Static indexing found 29,096 resource candidates and 11,490 recipe definitions.
The visual probe additionally loaded the genuine saved 18 November snapshot;
it was explicitly labelled cached evidence, not a live Minecraft connection.

| Resource | Translation found (en / ru) | Model | PNG texture decoded | Seen in actual UI |
|---|---|---|---|---|
| minecraft:diamond | Diamond / Алмаз | yes | yes | yes |
| minecraft:iron_ingot | Iron Ingot / Железный слиток | yes | yes | yes |
| minecraft:stone | Stone / Камень | yes | yes, flat block sample | yes |
| minecraft:tnt | TNT / Динамит | yes | yes, flat block sample | yes |

The external test also verifies gold_ingot, oak_planks, crafting_table, gunpowder
and the actual Ad Astra block-item translation for aeronos_planks. Evidence is
`.manual-tests/phase14-fixes/vanilla-evidence.json`.

The live desktop inspection confirmed readable pagination at **Page 530 of 530**
with Next/Last disabled, localized Aeronos Planks, four required Vanilla cards,
and only the Diamond card highlighted when selected. Opening the actual BOP TNT
recipe confirmed the 64px grid and one arrow. Minecraft was not launched anew.

`RealRecipeEditorUiTest` opens a real JavaFX dialog with the genuine imported TNT,
clears a selected input using the actual button, exercises draft Undo/Redo, edits
Raw JSON, returns to Visual, opens/cancels the actual Result Picker, saves and
reopens a project file, and checks exact saved UI text, JSON semantics and source
provenance. A first assertion incorrectly compared CRLF with TextArea-normalized
LF; the assertion was corrected without changing persistence or relaxing JSON checks.
Final successful runs preserve the source and result count 2 in the temporary project.

UI evidence: `.manual-tests/phase14-fixes/editor-ui.png` and `gui-project.json`.
These are test artifacts, not recipes installed into Minecraft. No source JAR or
modpack file was modified. The five source screenshots remain unavailable.

## Limitations

- Previewing a block face is not a full 3D Minecraft item renderer. Stone/TNT
  correctly retain RENDERING_UNSUPPORTED for geometry while their PNG samples display.
- Atlas palette permutations such as armor-trim model variants are not rendered.
  Their generated sprite references may show TEXTURE_NOT_FOUND; those model paths
  are labelled static candidates, not claimed registered ItemStack variants.
- Other launchers or custom shared-library layouts outside the supported explicit
  locations may require future configuration; missing files produce diagnostics.
- Unknown pack precedence is not guessed. Runtime confirmation in the visual probe
  refers to the saved snapshot's environment/time only.
- Windows 125%/150% system DPI was not changed or claimed tested. JavaFX layout/state
  tests cover 1280×720, 1920×1080 and 2560×1440 logical sizes; manual desktop
  inspection used the actual 1322×852 window and recipe dialog.
- No new release, tag, package installation or live Minecraft reload was performed.

## Commits for this continuation

- `13b64aa` — `fix(ui): improve resource browser pagination`
- `6af9a41` — `fix(resources): resolve vanilla minecraft textures and names`
- `9f9e34b` — `fix(ui): correct resource card selection highlighting`
- `abc7a16` — `fix(localization): restore localized item display names`
- `85046aa` — `fix(editor): improve crafting grid layout and controls`
- `d06e0fd` — `fix(editor): preserve tag identity in ingredient previews`

Additional real GUI probes/regression coverage and this report are committed
separately under `test(ui): add resource browser and recipe editor regression tests`.

## Final verification

| Check | Result |
|---|---|
| `gradlew.bat clean build` | BUILD SUCCESSFUL, 3m 11s, 82 executed tasks |
| Separate `gradlew.bat test` | BUILD SUCCESSFUL, 1s; results up-to-date after clean build |
| Unit/component | 314 tests: 312 passed, 2 Windows symlink skips, zero failures/errors |
| Reproducible integration | 54 passed, zero failures/errors/skips |
| Final real-pack external run | 3 passed, zero failures/errors/skips, 53s |
| Actual editor GUI workflow | Clear, Undo/Redo, Raw/Visual, Result Picker, Save, persistence reopen and provenance passed |
| Manual graphical probe | Required Vanilla cards, individual highlight, first/last pagination and actual TNT layout observed |
| Logical UI size cases | Resource state/layout checks at 1280×720, 1920×1080 and 2560×1440 passed |
| Normal `gradlew.bat :app:run` | Started Minecraft Recipe Editor 1.1.0-alpha.1; protocol 1 / Companion 0.1.0 / project format 1 |
| `git diff --check` | Passed |

External command:

```powershell
.\gradlew.bat :app:externalTest --tests '*VanillaResourceLoadingTest' --tests '*RealRecipeEditorUiTest' --tests '*RealRecipeProvenanceTest' '-PrealModpack=G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh'
```

The visual probe used `:app:previewResources` with the same realModpack and the
existing `runtimeSnapshotFile` property. It lives only on the test classpath and
uses the production ResourceBrowserView/RecipeEditorDialog. Its own temporary
project controls never write to the imported instance. Its Gradle process was
cancelled after inspection, without unsaved project changes. Normal launch remains:

```powershell
.\gradlew.bat :app:run
```

Local logs: `.manual-tests/phase14-fixes-build.log`, `phase14-fixes-test.log`,
`phase14-fixes-real-tests.log`, `phase14-fixes-visual.log`, `phase14-fixes-app.log`.
The source screenshots, live Minecraft, Windows 125%/150% DPI and installer matrix
are not presented as verified. The version remains alpha; no stable-release claim.

## Changed source files

- `PHASE14_UI_RESOURCE_FIXES.md`
- `app/src/test/java/dev/recipeeditor/app/RealRecipeEditorUiTest.java`
- `app/src/test/java/dev/recipeeditor/app/ResourceBrowserPreview.java`
- `app/src/test/java/dev/recipeeditor/app/VanillaResourceLoadingTest.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/CraftingRecipeEditorView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/IngredientPicker.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/MaterialsBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/PaginationView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeArrowView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeEditorDialog.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeOutputEditor.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeSlotView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeValidationPresenter.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceBrowserViewModel.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceCardView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ShapedCraftingEditorView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ShapelessCraftingEditorView.java`
- `desktop-ui/src/main/resources/dev/recipeeditor/ui/theme.css`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/CraftingRecipeLayoutTest.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/PaginationViewTest.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/RecipeSlotViewTest.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/ResourceBrowserStateTest.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/ResourceBrowserViewModelTest.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/ResourceCardSelectionTest.java`
- `registry/src/main/java/dev/recipeeditor/registry/LocalizationService.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceScanner.java`
- `registry/src/main/java/dev/recipeeditor/registry/SourceType.java`
- `registry/src/main/java/dev/recipeeditor/registry/TextureResourceService.java`
- `registry/src/main/java/dev/recipeeditor/registry/VanillaAssetLocator.java`
- `registry/src/test/java/dev/recipeeditor/registry/LocalizationServiceTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/ResourceScannerTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/VanillaAssetLocatorTest.java`
