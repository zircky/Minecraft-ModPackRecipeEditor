# Phase 19 — Steps 1–4

## Scope and Git

Implemented only the final authorized scope: Git preparation, rendering audit, GT Material Colors and GregTech Ore Rendering. Botania/floating flowers, additional queue/status/localization work and remaining Phase 19 steps await a separate command. Full Phase 19 is not declared complete.

Main checkout `L:\My Add\RecipeEditor`; branch `feat/phase-19-dynamic-item-rendering`. Base: completed local Phase 18 `ed95e10` on `feat/phase-18-true-3d-block-rendering`, which was not merged into `origin/master` (`c6d3f1e` at fetch). No merge or worktree was created. User resolved the version conflict explicitly: desktop **1.1.3-alpha.1**. Companion 0.1.0, protocol 1 and project formats unchanged.

[Rendering audit](PHASE19_RENDERING_AUDIT.md) · [Changed files](PHASE19_FILES.txt)

## Cause and implementation

Installed GTCEu is **8.0.0-SNAPSHOT+e24b238**, JAR `gtceu-1.20.1-8.0.0-SNAPSHOT+20261104-224207-e24b238.jar`. Public APIs were checked against that JAR with javap and [pinned upstream sources](https://github.com/GregTechCEu/GregTech-Modern/tree/e24b238), including TagPrefixItemModelGenerator, OreBlockModelGenerator, MaterialIconType, MaterialIconSet, TagPrefixItem and MaterialBlock.

Dynamic form and ore Item Models are generated in GTDynamicResourcePack; a physical per-material JSON need not exist in the JAR. The general parser therefore reported MISSING_MODEL. Forge composite models required a renderer that understood their children. A saved registry snapshot also did not mean a live preview connection existed. No missing registration was inferred from missing JSON.

The existing GregTechMaterialCollector now retains actual getLayerARGB values, the actual Icon Set parent chain and registered form -> icon type/ore base model mappings. Its existing strict e24b238 ABI gate remains. MaterialColor separates opaque RGB from explicit RGBA and multiplies straight RGB/alpha independently. GT's own opaque-alpha material API rules are retained rather than assigning arbitrary transparency or colors.

GregTechMaterialPreview selects actual templates along the captured Icon Set chain. Generated item layers are tinted once using the captured layer colors. Ore support handles the verified composite order: original base rock, then material overlay layers. Compatible coincident geometry/UV is composed per face before rendering the original geometry, avoiding coplanar meshes/z-fighting and tinting only the ore. Unsupported geometry, emissive tint indices, tinted bases, ambiguous templates or missing metadata remain native-required.

InventoryPreviewProvider retains native ItemRenderer priority and reuses the same renderer/cache for Resource Browser, Ingredient Picker and recipe slots. The fallback is based on captured material relationships rather than item-name patterns. Material metadata identity participates in the cache context alongside snapshot/files/runtime context. Available runtime texture hashes are checked before loading template textures. No separate renderer or new material registry was introduced.

## Real verification

Actual ISG Minecraft 1.20.1 / Forge **47.4.28** was launched with the updated distributable Companion. A separate superflat creative world `MRE Phase19 verification` was created to obtain a fresh complete registry/material snapshot. Existing worlds were not opened. Old Companion JAR was backed up in `.manual-tests/phase19/audit/backup/`; the newly built JAR remains installed in the authorized test pack.

`GregTechDynamicRenderingEvidenceTest` passed in **5m21s**. It obtained actual registry/material data, selected registered IDs, imported the pack's real static assets, captured native ItemRenderer PNGs and independently rendered static fallback PNGs. All ten samples had native READY and nonempty static images using the dynamic template path:

- steel and bronze: ingot, dust, plate (six forms);
- green sapphire ore: stone, deepslate, netherrack, endstone (four variants).

The actual ID is `gtceu:netherrack_green_sapphire_ore`; the task's `netherack` spelling was not silently used as a fabricated item.

[Native/static comparison](phase19-evidence/native-static-comparison.png) · [Actual metrics and IDs](phase19-evidence/metrics.json)

The comparison sheet was visually inspected: forms, material colors, rock textures, ore overlays, orientation and scale agree closely; JavaFX lighting/sampling differs. Static images are approximations, not pixel-perfect captures. User screenshots referenced in the attachment were not attached; no comparison to them is claimed. Placeholder output is not counted as success.

| Mod | Resource type | Before (offline) | After | Verified |
| --- | --- | --- | --- | --- |
| GregTech | Steel/bronze ingot | Missing dynamic per-item JSON | Captured Icon Set template + actual layer colors | Native/static PNG comparison |
| GregTech | Steel/bronze dust | Missing dynamic per-item JSON | Correct dust template and material colors | Native/static PNG comparison |
| GregTech | Steel/bronze plate | Missing dynamic per-item JSON | Correct plate template and material colors | Native/static PNG comparison |
| GregTech | Green sapphire ores | Missing generated/composite models | Untinted actual rock + colored ore layers, actual 3D geometry | Four real rock variants compared |
| Botania | Floating flower | Native/custom renderer requirement | Not changed in Steps 1–4 | Deferred |

Native capture and static fallback are shared production code. No new manual UI surface work was performed because Step 7 is outside this request's final scope.

## Tests and limitations

Added MaterialColorizationTest, MaterialIconSetResolverTest, GregTechMaterialPreviewTest and GregTechOrePreviewTest, plus the actual external evidence test. Tests cover explicit RGB/RGBA, alpha, grayscale/masks, distinct layers, parent Icon Set fallback, stale metadata rejection, untinted rock and different rock sources. Fixtures are explicitly synthetic; actual Minecraft proof is recorded separately above.

A fresh snapshot from the updated Companion is required for offline dynamic forms. Older snapshots remain readable but lack the evidence needed to invent colors/templates. Support is gated to verified GT8 e24b238 rules and compatible JSON templates; custom loaders, unknown transforms/UV composition, resource ambiguity, emissive and unsupported icon sets use native rendering or explicit diagnostics. No exhaustive support claim for all GT materials, ItemStack variants or custom resource packs is made.

Low RAM slowed the real-client run; full build runs after the client closes. A user Escape stopped Computer Use after successful capture. On resume the Minecraft window disappeared before any additional input, so graceful automated shutdown is not claimed. The test world remains available; it was not deleted.

## Final checks and commits

- `.\gradlew.bat clean build`: **BUILD SUCCESSFUL**, 3m34s, Java 21. 364 unit tests (2 environment-dependent symbolic-link skips), 98 integration tests, zero failures/errors. Architecture check: 12 modules, no cycles.
- `.\gradlew.bat test`: **BUILD SUCCESSFUL**, 16s, up-to-date after clean build.
- Standalone Forge `.\gradlew.bat clean build`: **BUILD SUCCESSFUL**, 50s, Java 17. The resulting Companion was installed and used in the actual runtime test.
- Actual external test: **BUILD SUCCESSFUL**, 5m21s, ten native/static pairs visually inspected. Botania and the later Phase 19 steps were not tested or implemented here.
- `git diff --check`: clean.

Logical commits after base `ed95e10`:

1. `2eaa727` chore: bump version to 1.1.3-alpha.1
2. `4768a48` feat(rendering): support material texture colorization
3. `a720cbc` feat(gtceu): render material forms and ore textures
4. `test(rendering): add dynamic item rendering regression tests` — includes this report/evidence; its exact SHA is given in the final response and `git log`.

**Git Push:** branch published successfully to `origin/feat/phase-19-dynamic-item-rendering`; final evidence commit is sent with a normal follow-up push and remote HEAD checked before completion. No forced push, merge, tag, release or automatically created PR.

Run `.\gradlew.bat :app:run`. For offline dynamic GT previews, synchronize and save a fresh snapshot using the updated Companion, then import/open the pack with that snapshot. Native preview remains preferred whenever its client capability is connected. Full Phase 19 continues only on a separate command.

