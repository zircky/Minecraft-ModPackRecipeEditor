# Phase 17: Registry audit (Steps 1-4)

## Scope

This stage implements registry normalization and the unified Items browser only. Static 3D geometry and runtime client rendering are deferred to Steps 5-7. Application, protocol, project format and Companion versions remain unchanged.

## Findings

- `ResourceEntryBuilder` previously derived ITEM IDs from every `models/item/*.json` and BLOCK IDs from both `models/block/*.json` and `blockstates/*.json`. Door parts and other helper models therefore became false resource cards. The defect was in identity inference, not in Minecraft registration.
- `ResourceScanner` already preserves model JSON, blockstates, textures, atlases and their archive provenance. These assets must remain available to renderers and diagnostics.
- Runtime Item and Block registries are separate. Existing snapshots carry a `blockItem` flag but do not carry `BlockItem.getBlock()` IDs. Equality of Item and Block IDs cannot recover this missing relationship.
- `RegistryMergeService` preserved metadata but discarded runtime class and block state properties in the preferred registry. Those fields are now retained as presentation metadata.

## Decisions

`ResourceAssetCatalog` exposes typed asset records independently of `RegistryIndex` resource entries. Model files never establish resource identity. Blockstates supply static block candidates; tags and successfully parsed recipe operands supply independent static references. None of these assert runtime registration. All asset definitions retain their physical sources.

`UnifiedResourceIndex` combines presentation only: one card per Item ID, explicit BlockItem classification and confirmed blocks without Item. `ResourceIdentity` includes registry kind to distinguish equal Item/Block IDs. Duplicate conflicting definitions retain all sources and an ambiguous preview instead of choosing an arbitrary image.

`BlockItemRelationships` accepts explicit runtime links only for matching snapshot captures. Forge collectors use `BlockItem.getBlock()` and registered block keys. The block collector reports whether the complete Item registry contains a BlockItem targeting each block. An empty or old snapshot cannot establish absence. Optional metadata keeps the existing protocol compatible.

The Items browser offers All, Items, Block Items, Blocks without Item and All Blocks (technical). The technical filter preserves access to unknown blocks in older captures; removing the old navigation entry does not delete the Block domain. Cards and compact grid share the existing bounded preview service. Source, confidence, raw IDs, blockstates and runtime properties remain in tooltips/details. No geometry support is implied.

## Validation

Final measured counts, commands, commits and limitations are recorded in `PHASE17_REPORT.md`. Synthetic relationship fixtures do not count as a live Minecraft verification.
