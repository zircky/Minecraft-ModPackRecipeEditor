# Phase 18 inventory rendering audit

This task continues the main checkout on `feat/phase-18-true-3d-block-rendering`, starting at `5ae8a84`. Desktop version remains `1.1.2-alpha.1`; Companion `0.1.0`, protocol 1 and project formats are unchanged. No new worktree or interactive viewer was introduced.

## Why icons were flat

`ResourcePreview` previously called `TextureResourceService.previewAsync(ResourceVisual, root)`. `ModelTextureResolver` followed textures, while `RuntimeResourceResolver` supplied baked-model sprite evidence. Both produced PNG layers, not a rendered ItemStack. `RecipeSlotView` and Ingredient Picker reused that same flat path. The existing selected-block geometry viewer was independent of catalog icons and did not solve this problem.

The old path could show a block texture sample even where the actual item model was a stairs, slab or fence inventory model. Runtime sprite metadata also cannot reproduce custom ItemStack renderers. Neither a namespace nor an arbitrary `textures/block/` image establishes the correct item representation.

## Source checks

Implementation was checked against the locally mapped Minecraft 1.20.1/Forge sources: `ItemTransform.Deserializer`, `ItemRenderer.getModel`, `GuiGraphics.renderItem`/`flush`, `TextureTarget`, `RenderSystem`, `NativeImage.downloadTexture`/`flipY`. Development Forge is 47.4.10; the actual ISG client used for distribution verification is Forge 47.4.28. No server renderer or guessed remote method signature is used.

## Replacement

Resource ID -> registered Item or verified BlockItem relationship -> optional client ItemStack GUI capture -> resolved item-model static rendering -> suitable generated 2D texture -> explicit diagnostic placeholder. One application provider serves browser, picker, recipe browser and recipe inputs/outputs. Cards contain ImageViews; the static renderer reuses one offscreen scene, rather than allocating a SubScene per card.

Static JSON processing resolves `models/item/`, parents, inherited GUI transforms, elements, UV/face rotation, atlas references and explicit tint-index evidence. Custom loaders, predicates, ambiguous definitions and absent item models require the client or a placeholder. No blockstate or particle texture is substituted as a successful block icon.
