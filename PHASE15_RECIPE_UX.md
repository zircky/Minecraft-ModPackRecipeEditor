# Phase 15 — Recipe Editor UX and Ingredient Selection

Version remains `1.1.0-alpha.1`. Work continues on
`feat/phase-15-recipe-editor-ingredient-ux`. No merge, tag or release is created.

## Implementation

- Recipe Browser derives its name and image from a declared output through the
  existing registry/localization data. Recipe IDs stay secondary, searchable and
  available in Details. Equal result names do not merge separate definitions.
  Dynamic/unknown results are labelled explicitly. A representative known output
  of a multi-output recipe has an additional-output count; it is not claimed to
  be the sole or semantically primary output of an unknown mod serializer.
- `RecipeTypeDisplayNameResolver` registers localized type and machine names by
  exact serializer ID. Nine Vanilla serializers have English/Russian labels.
  Extensions can register verified labels; an unknown serializer retains its ID.
- `RecipeLayoutResolver` produces immutable `RecipeLayoutModel` presentation data.
  `RecipeLayoutRenderer` consumes it in Viewer, Editor and Creator modes. This
  code resides in desktop-ui; recipe-engine remains independent of JavaFX.
- One configured cooking provider handles smelting, blasting, smoking and
  campfire cooking. Input, arrow and output share one GridPane row; labels and
  cooking parameters occupy separate rows. Stonecutting preserves output count;
  smithing has template/base/addition roles; trim output stays dynamic. Shapeless
  uses array semantics, displayed in up to nine cells without positional JSON.
- Shared slot size defaults to 64 px and is adjustable from 40 to 80 px. Shaped
  output and arrow align with the center of the 3x3 grid. Viewer can navigate
  exact resource references to Resource Browser without changing the recipe.
- Editor opens Ingredient Picker directly from an input slot. Context menus
  clear inputs; the result slot selects result/quantity. Creator starts with an
  incomplete empty template and cannot save until validation succeeds.
- Ingredient Picker has explicit Exact Item / Tag modes, indexed containing-tag
  suggestions, member previews, a visual resource grid and explicit alternative
  replacement/add/remove actions. `IngredientSelectionModel` retains unmodified
  alternative JSON nodes, including opaque/custom fields; accepting an existing
  selection does not flatten it or append a duplicate choice.
- Form patches continue through the existing draft Undo/Redo, validation,
  RecipeChangeSet and persistence pipeline. Cooking string results remain strings;
  pre-existing extended object results retain their nested fields. Stonecutting
  and object-result picker defaults retain the existing output quantity.
- Fixed a real mounted-view race: pre-created recipe views could finish texture
  loading while hidden and discard the completed image. ResourcePreview now loads
  on scene attachment. This also applies to generated/runtime GTCEu previews.
- Old independent CraftingRecipeEditorView/RecipeOutputEditor were removed.
  The existing shaped/shapeless grids are children of the shared renderer.

## Verification

- `./gradlew.bat clean build`: BUILD SUCCESSFUL, 4m 22s, 82 executed tasks.
- Unit suite: 322 tests, 320 passed, 2 platform-dependent symlink tests skipped.
- Reproducible integration suite: 74 tests passed, no failures/errors.
- Separate `./gradlew.bat test`: BUILD SUCCESSFUL, 31s (all tasks up-to-date after clean build).
- `:app:externalTest --tests '*RealRecipeEditorUiTest' -PrealModpack=...`:
  BUILD SUCCESSFUL, 35s; one genuine-pack UI/persistence test passed.
- `./gradlew.bat :app:run`: actual main window opened; toolbar/status show
  `1.1.0-alpha.1`, startup logs confirm protocol 1, Companion 0.1.0 and project
  format 1. The normal window is left open on the empty Projects screen.
- `git diff --check`: passed.

Regression coverage includes all four cooking serializers, stonecutting,
smithing transform/trim, shaped/shapeless, empty creators, row alignment/scaling,
localized names, duplicate-result rows and technical/machine search, read-only
viewer actions, direct edit callbacks, alternative JSON preservation, empty
alternative rejection and delayed attachment of cached texture previews.

Actual read-only pack: `G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh`.
The visual resource probe uses the genuine saved runtime snapshot
`.manual-tests/resources/isg-materials.mresnapshot`, not a new live connection.
Static indexing found 29,096 resources and 11,490 recipe definitions. The merged
resource probe contained 31,752 items (29,442 runtime-confirmed).

Desktop interaction verified:

- `gtceu:steel_ingot`: correct available runtime sprite layers/tint, name and
  selected-card highlight; resource details keep namespace/source confidence apart.
- `minecraft:tnt` from the actual BOP archive: localized result name, thumbnail,
  nine image slots, tag badges and one centered arrow. All table status columns
  remain visible in the normal split pane after constrained column sizing.
- Ad Astra Glacio Stone smelting: result name, input/output PNGs, cooking time
  200 and experience 0.1, equal slots and centered arrow. Switching en_us to ru_ru
  updates result/type/machine and slot tooltip names. Changing 64 to 72 px keeps
  alignment.
- BOP Brimstone Brick Slab stonecutting: image input/output and actual count 2;
  missing Russian result translation falls back to the available English name.

The external RealRecipeEditorUiTest operates the actual JavaFX TNT editor:
clear via slot context menu, Undo/Redo, Raw/Visual switching, output and tag
pickers, save a test ChangeSet, disk reopen and source-provenance equality.
Its project and screenshot are generated under `app/build`, never installed in
Minecraft or written over the user's existing manual projects.

## Boundaries

- New shared visual layouts cover the nine listed Vanilla serializers. Create,
  Mekanism and unverified GT8 recipe serializers are not declared visually
  supported or rendered as fake crafting recipes. The existing capability-gated
  GT7.1.4 leaf editor remains; a full shared mod-machine renderer is not added.
- Images reuse the existing bounded flat texture/layer renderer. Arbitrary 3D
  geometry, overrides and atlas-only sprites are not full Minecraft rendering.
  Missing/ambiguous resources retain their diagnostic status.
- Native desktop checks exercised the genuine recipes listed above. All other
  Vanilla template/layout cases are covered by JavaFX/JUnit integration tests;
  they are not claimed to have been manually clicked in Minecraft.
- Minecraft was not launched anew, and no live reload or export into the pack
  was performed. Pack archives, worlds and user project edits were preserved.

## Run

```powershell
.\gradlew.bat :app:run
```

For the optional genuine resource probe:

```powershell
.\gradlew.bat :app:previewResources `
  "-PrealModpack=G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh" `
  "-PruntimeSnapshotFile=L:\My Add\RecipeEditor\.manual-tests\resources\isg-materials.mresnapshot"
```

The probe lives only on the test classpath and is not production sample content.
