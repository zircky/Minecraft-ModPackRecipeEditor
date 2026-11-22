# Phase 19 — rendering audit (work in progress)

Scope authorized by final attachment instruction: Steps 1–4 only, then tests, logical commits and GitHub push. Botania/remaining steps require another command. User resolved the version contradiction: desktop 1.1.3-alpha.1. Companion/protocol/project versions stay unchanged.

Main checkout, branch feat/phase-19-dynamic-item-rendering, base local completed Phase 18 ed95e10. Origin/master=c6d3f1e; Phase 18 not merged/pushed. Fetch done; initial tree clean; 15 base commits ahead of origin/master. No merge/worktree.

Installed GT: gtceu-1.20.1-8.0.0-SNAPSHOT+20261104-224207-e24b238.jar. Public API checked with javap on this JAR and sources at https://github.com/GregTechCEu/GregTech-Modern/tree/e24b238 (not unverified older ABI). Downloaded audit source files are in ignored build/phase19-audit.

TagPrefixItemModelGenerator creates delegated item model JSON in GTDynamicResourcePack. OreBlockModelGenerator creates per-prefix base-rock/composite templates and delegates registered item IDs. There is no green_sapphire item-model JSON in the physical JAR. Missing physical JSON is not missing registration. Existing static ItemModelResolver correctly reports MISSING_MODEL; custom composite loader is unsupported by general parser. Phase 18 native capture already renders registered stacks, but a saved snapshot is not an active rendering connection.

MaterialIconType selects template models by the material icon-set parent chain and existing resources. MaterialBlock/TagPrefixItem tint comes from getLayerARGB(index). The installed Material implementation forces material ARGB alpha opaque, returns white outside color layers, and falls back to primary when appropriate. Existing collector did not retain colors/icon sets/template selections. New metadata must extend that collector, not create another material registry.

Ore template inspected in actual JAR: forge:composite, children base_stone and ore_texture, item_render_order base_stone then ore_texture. Overlay elements carry tintindex 0/1. The base parent comes from actual TagPrefix.ORES OreType.baseModelLocation(); it must not be guessed from resource ID or tinted with the overlay. Arbitrary geometry/custom icon sets remain native-required unless the exact supported composition is verified.

Screenshots mentioned in the request were not attached. Actual native/static PNGs were compared visually; no comparison to unavailable user screenshots is claimed.


Current implementation: MaterialColor defines explicit opaque RGB vs RGBA and straight-alpha per-channel multiplication. Existing shared preview provider gains GT-only fallback from captured Material forms/colors/icon sets, never replacing an existing physical Item Model. For supported congruent ore geometry/UV, per-face base+overlays are composited before rendering the actual base geometry, avoiding overlapping coplanar meshes/z-fighting. Active texture hashes are checked when available; unknown/emissive/custom composition still requires native rendering. Cache context now includes material metadata identity. No per-screen renderer added.

Final evidence: GregTechDynamicRenderingEvidenceTest passed against an actual Forge 47.4.28 ISG client with the new Companion and a fresh runtime snapshot. Ten registered steel/bronze forms and green sapphire rock variants produced both native and static images. See PHASE19_REPORT.md for verification and scope. A separate superflat creative world named MRE Phase19 verification was created; existing worlds were not opened. Computer Use was stopped by user Escape after capture, and on resumption the game window disappeared before further input; no claim of an automated graceful shutdown is made. Backup/source audit/logs are under ignored .manual-tests/phase19/audit, snapshot under .manual-tests/phase19/verified-materials.mresnapshot.
