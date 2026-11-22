# Phase 18 — Minecraft inventory icons

Main checkout: `L:\My Add\RecipeEditor`. Branch: `feat/phase-18-true-3d-block-rendering`. Desktop `1.1.2-alpha.1`, Companion `0.1.0`, protocol 1; project formats unchanged. No tag, release, merge, push or new worktree. This report supersedes the earlier selected-model viewer report for **catalog inventory icons**; the earlier geometry foundation is retained.

## Architecture and implementation

See [audit](PHASE18_INVENTORY_AUDIT.md) and [changed files](PHASE18_INVENTORY_FILES.txt).

`InventoryPreviewProvider.shared()` is application scoped. It resolves actual registered item IDs (or an explicitly captured BlockItem relationship) and prefers optional authenticated client capture. Static fallback resolves the real Item Model, its parent chain, GUI transform and geometry. All item surfaces mount the same `ResourcePreview`: browser cards/details, Ingredient Picker, recipe browser, input/output slots, viewer and creator. Existing fluid texture previews are retained. Materials' representative form samples are outside this item's surface replacement.

Model/texture parsing, archive access and PNG encoding run outside JavaFX. One reusable offscreen SubScene snapshots static models; mounted cards contain ImageViews. Visible cells queue images on mount, cancel their consumer on unmount, and refresh on context revision. Queue limit 128, batch 4, up to 2 registry contexts and 128 parsed models/context. The image LRU is bounded by 256 icons and 8 MiB. The key includes ID/kind, variant, NBT SHA-256, Minecraft 1.20.1, registry/snapshot and resource file signature, native resource-pack context, size and renderer version. Changed local resources require re-indexing instead of rendering stale model JSON. Disconnect/context changes invalidate cached images; live defaults refresh periodically for dynamic rendering.

`item-previews-v1` is optional within protocol 1. ITEM_PREVIEW_REQUEST/RESPONSE/ERROR contain bounded data, never game objects or Java serialization. Sizes 16/32/64/128, batch 1..4, NBT up to 8192 characters/depth 32, PNG up to 96 KiB, dimensions checked before desktop decode. Client queue 8, native render queue 16 and PNG queue 8; cancellation, timeouts and backpressure retain diagnostics. Old/dedicated Companions do not advertise client rendering.

Forge's client-only `ClientInventoryCapture` resolves registered Items and copies their default stacks, or applies explicit bounded NBT. On RenderTick END it captures at most one icon/frame with `GuiGraphics.renderItem` (the actual ItemRenderer GUI path) into a transparent TextureTarget. It restores framebuffer/viewport/projection/model-view, relevant texture/shader/blend/depth/cull/scissor/color state. PNG encoding runs on a bounded worker. Missing baked models, empty frames, pack changes and renderer failures return errors. Client rendering classes are not loaded by the common dedicated-server bridge.

Static models support parents, GUI transforms including right_rotation, actual elements, per-face textures, UV/face rotation, atlas mappings and explicitly known tint indices. A door resolves its generated inventory model; fence/stairs/slab resolve their actual inventory geometry. Generated 2D models use composited layer PNGs. No missing/custom model is replaced by a particle texture, neighbour icon or generic cube.

## Actual Minecraft verification

The isolated vanilla Forge 47.4.10 client rendered all ten required resources plus healing/poison potion NBT variants; the PNGs differed as expected. A real resource-pack reload changed preview context from epoch 1 to 2 and subsequent capture succeeded. This is an actual GPU/client check, separate from synthetic protocol fixtures.

The final distributable Companion was also installed in the user-authorized ISG `0.6.0-alpha.1-harsh` (Minecraft 1.20.1, Forge **47.4.28**, 81 loaded mods including library dependencies). Its previous JAR was backed up; no world was opened. The client was closed from the title screen after verification. No recipes or pack configuration were edited.

**24 default-stack native captures passed**, including all ten required vanilla resources and:

- GregTech: steam/heatproof/frostproof machine casings, **lv_macerator**, **aluminium_fluid_cell**.
- Ad Astra: aeronos/strophar/glacian planks.
- Botania: livingwood planks, stairs, slab.
- NuclearCraft: fission/fusion reactor casings and fusion reactor casing glass.

Every successful native response had the requested identity/size, native source, nonempty transparent PNG and nontransparent geometry. Healing and poison potion captures differed. Create and Mekanism were absent and were **not tested**. This is evidence for these registered default stacks, not every mod item/NBT/state.

[Actual client inventory sheet](phase18-inventory-evidence/client-inventory.png), [native metrics](phase18-inventory-evidence/client-metrics.json). The custom GT machine and cell were also visually inspected individually.

## Static verification

With Minecraft disconnected, actual models/textures from ISG were imported (71 JARs, 72 mod definitions). All ten vanilla resources passed with transparent backgrounds: seven actual geometry icons; door, diamond and iron ingot remained generated 2D items. Real Botania, Ad Astra, GT circuit and NuclearCraft samples were exercised. `gtceu:aluminium_fluid_cell` correctly returned an unsupported-custom-loader placeholder offline, while the actual client rendered it successfully.

[Static inventory sheet](phase18-inventory-evidence/static-inventory.png), [static metrics](phase18-inventory-evidence/static-metrics.json). Form and GUI orientation were compared visually with the native sheet. JavaFX lighting differs; static output is explicitly labelled an approximation.

## UI verification

The test-classpath ResourceBrowserPreview harness used the **production** ResourceBrowserView, IngredientPicker and RecipeEditorDialog, the genuine ISG registry snapshot (29,442 registered items), imported static resources and a live connection to the actual ISG client. The browser showed Ad Astra cubes, stairs, slab, fence and flat door. Searching/selecting `minecraft:oak_stairs` showed its correct shape at card/detail sizes and `READY: MINECRAFT_CLIENT_INVENTORY` in details. The TNT editor displayed 3D sand inputs and TNT output; the picker displayed the same Ad Astra inventory shapes. Picker cancelled, draft unchanged, editor and harness closed.

The supplied request references screenshots 2–6, but they were not attached to this message. No direct comparison to unavailable screenshots is claimed; the actual Minecraft GUI renderer was the reference.

## Automated verification

All eight requested test classes were added, plus bounds, texture tint and invalidation tests. Coverage includes actual item-model resolution, inherited GUI transform, geometry/transparent snapshot, unsupported/missing models, authenticated batch transport and identity validation, bounded queue/cancellation/disconnect, LRU/key isolation, snapshot/runtime/resource-file invalidation, explicit BlockItem mapping and all shared UI surfaces. Synthetic transport/UI fixtures are labelled as such and are not presented as Minecraft rendering evidence.

Requested classes: `InventoryItemModelResolverTest`, `GuiDisplayTransformTest`, `StaticInventoryRendererTest`, `ClientItemPreviewProtocolTest`, `ItemPreviewCacheTest`, `ResourceBrowserPreviewTest`, `IngredientPickerPreviewTest`, `RecipeSlotPreviewTest`.

The additional explicit BlockItem test exposed a batch construction bug: an entry was added to the pending runtime batch before resolving its item relationship. A missing relationship could leave an unmatched pending entry. Request construction now completes before either batch list is changed; unknown technical blocks produce a placeholder and no network request.

Verification uses Java 21 for desktop and Java 17/its own Gradle wrapper for Forge, with one worker and 768 MiB Gradle heap. A concurrent Minecraft/build attempt timed out in an existing JavaFX test under memory pressure; that run was stopped and checks repeated after closing only the test client. One native probe initially collided with the browser's active connection (the Companion accepts one authenticated desktop connection); it passed after the browser was closed.

The dedicated Forge 47.4.10 GameTest and real desktop snapshot/auth/sync/reconnect probe both passed, proving the common listener still loads on a server without loading client rendering classes. The distributable JAR contains no test probe classes. Its SHA-256 matches the JAR installed and actually tested in ISG: `6b5ee607fd6ec4cd73253757eca2a283b77f871e9fd934a0188f32abcb012459`.

Final build/test summary is appended below.

## Boundaries

- Native preview is implemented for Forge Minecraft 1.20.1. It captures a registered default stack or the supplied NBT, not every possible creative tab variant, stack capability, custom animation/world condition or server-dependent state.
- Static rendering requires JavaFX SCENE3D/GPU support. Compatible identity-transform generated 2D textures can still be used without a 3D snapshot; otherwise an explicit placeholder is shown.
- Custom loaders, ItemStack predicates/overrides, builtin/custom renderers, ambiguous pack definitions, UV outside the parser's supported 0..16 range and missing resources require native capture or a diagnostic placeholder. Generated-item edge extrusion, exact vanilla lighting and live animation are not reproduced by static 2D planes; texture animation uses the existing bounded first-frame logic.
- Unknown biome/runtime tint is diagnosed rather than invented; old snapshots without per-index tint cannot supply it reliably. Old snapshots without explicit BlockItem mappings require a fresh runtime sync to map technical block entries. Equal IDs are not evidence of a relationship.
- Resource-pack change detection invalidates frames but does not automatically rebuild a stale static index. Re-import/re-sync when prompted. Native resource reload/session/world changes produce fresh contexts.
- No public release or exhaustive support claim for all modded resources is made.


## Final verification and commits

- `clean build`: **BUILD SUCCESSFUL**, 3m55s; 355 unit tests (2 environment-dependent symbolic-link skips), **98 integration tests**, no failures/errors. Architecture check: 12 modules, no dependency cycles.
- Separate `.\gradlew.bat test`: **BUILD SUCCESSFUL**, 22s (up-to-date after the clean build).
- Standalone Forge `clean build`: **BUILD SUCCESSFUL**, 1m08s, Java 17; release JAR excludes probe/GameTest classes.
- Dedicated Forge GameTest: **BUILD SUCCESSFUL**, 4m32s; desktop actual-runtime probe: **BUILD SUCCESSFUL**, 30s (registries, tags, recipes, snapshot round trip, re-sync and reconnect).
- Actual ISG native default/NBT captures: **BUILD SUCCESSFUL**, 39s after closing the competing browser connection.
- Final offline static evidence: **BUILD SUCCESSFUL**, 42s. Evidence images/metrics are retained in this report folder, outside transient build outputs.

Commits (after base `5ae8a84`):

1. `5e12511` refactor(rendering): prioritize minecraft inventory item models
2. `d143a73` feat(companion): support client-side item icon capture
3. `919111e` feat(rendering): add inventory-style block model rendering
4. `7748403` feat(ui): display minecraft inventory icons in resource browser
5. `008e68d` feat(editor): reuse inventory icons in recipe slots
6. `0318809` fix(rendering): validate block item before queuing preview
7. Final test/documentation/evidence commit: `test(rendering): verify minecraft inventory item previews` (find with `git log`; this report is included in that commit).

No push was performed. Run the desktop with `.\gradlew.bat :app:run`; connect the updated Companion through the existing session-file workflow for native icons. Without a client, the static item-model fallback is automatic. Raw verification logs and the pre-test ISG Companion backup are kept under ignored `build/phase18-inventory-verification/`; Gradle clean may remove them. The durable PNG/JSON evidence is in `docs/reports/phase18-inventory-evidence/`.
