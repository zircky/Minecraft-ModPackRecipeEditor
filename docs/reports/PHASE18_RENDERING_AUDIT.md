# Phase 18 — Rendering Audit (Step 2)

## Scope

Requested stage: Steps 1-6 only, through a working interactive 3D scene. Blockstate selection, catalog thumbnails, recipe slot integration, runtime client providers and thumbnail cache are later steps. No claim that the existing browser already uses 3D.

Work is in the main project directory, branch `feat/phase-18-true-3d-block-rendering`, based on `c6d3f1e`. Phase 17 is present through merged PR #6. Initial Git status was clean. No additional worktree was created.

The attachment contradicted itself about version changes. The user explicitly resolved this: update desktop version to `1.1.2-alpha.1`. Companion/protocol/project-format versions remain unchanged.

## Actual existing pipeline

- `ResourcePreview` mounts an `ImageView` from `TextureResourceService.previewAsync`, with existing bounded texture caches and workers.
- `ModelTextureResolver` follows parent texture references and selects flat texture layers or a labelled representative blockstate sample. It does not create vertices or triangles.
- `RuntimeResourceResolver` maps runtime baked-model sprite metadata to textures. Those sprites do not contain model geometry.
- `RecipeSlotView` uses the same flat preview path.
- No `ResourcePreviewProvider`, `BlockModelResolver`, `BlockstateResolver`, `StaticBlockPreviewRenderer` or `RuntimePreviewProvider` classes existed under these exact names. No existing `TriangleMesh`/`MeshView` block geometry was found.

The earlier code therefore could not show a true rotating model: it had only decoded PNG pixels, not a three-dimensional scene. A baked-model texture sample remains explicitly different from a rendered mesh.

## Architecture introduced in Steps 3-6

`ResourceAssetCatalog` → `BlockModelParser` → JavaFX-independent `BlockGeometryModel` → `BlockMeshBuilder` → per-face `TriangleMesh`/`MeshView` → `BlockPreview3DView` with SubScene, PerspectiveCamera and lights.

Each quad has four position/UV vertices and two actual triangles. Face/material boundaries are explicit, allowing later material or atlas batching. Element rotation uses its own origin, axis and angle; rescale applies only perpendicular to its rotation axis. Coordinates convert Minecraft `(x,y,z)` to JavaFX `(x-8,8-y,8-z)`. Model proportions are preserved; scene normalization uses one uniform scale.

Parent geometry is inherited; a child elements array replaces parent elements. Child texture variables override parent bindings. Parent cycles, absent resources, ambiguous archive definitions, invalid geometry/UV and custom loaders produce explicit diagnostics. No unknown model is replaced with a cube.

Default UV formulas and valid element angles were checked against the locally cached mapped Minecraft 1.20.1 `BlockElement` and `BlockFaceUV` sources, rather than guessed from texture filenames.

`BlockPreviewCamera` owns bounded orbit and zoom state; the scene handles primary drag, wheel, double click and Reset View. Parsing and texture reads run outside the FX thread; scene graph creation runs on the FX thread. There is one scene for the selected model, not one scene per resource card.

A test-classpath visual launcher `:app:previewBlockModels` displays genuine vanilla model files from a locally imported instance. It uses the production parser, builder and scene. It is not catalog integration and does not imply live Minecraft rendering.

## Known boundaries for this stage

No blockstate variant/multipart selection or uvlock yet (Step 7). No catalog/recipe icon integration or 3D thumbnail cache yet (Steps 8-9). Runtime/custom renderers remain unsupported. Biome tint requires runtime data; the view explicitly labels untinted geometry. Cullface metadata is preserved, but isolated previews do not omit faces based on neighbouring blocks. Supported UV coordinates are currently 0..16; wrapping UV outside that range is diagnosed. Animated textures use the existing first-frame texture path.

Final build, test and visual results are recorded separately in the stage report after verification.
