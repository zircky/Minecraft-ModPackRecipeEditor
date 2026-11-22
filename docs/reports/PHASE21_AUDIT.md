# Phase 21 — Client item preview pipeline audit

Version remains **1.1.3-alpha.1**, Companion **0.1.0**, protocol **1**, project format **1**.
Branch: `fix/phase-21-client-item-preview-pipeline`, based on the existing Phase 20 checkout.
The user confirmed the problem occurred in the new portable 1.1.3; the separately installed 1.0 application is not the explanation.

## Existing executable path

| Stage | Production code | Verification |
| --- | --- | --- |
| Client registration | `ClientInventoryCapture.setup` installs `RuntimeItemPreviews` during FML client setup | Real client advertises `item-previews-v1` |
| Request transport | `CompanionConnectionService.itemPreviews`, `ReadOnlyCompanionServer` | Authenticated TCP; request/response identity checked |
| Queue | `ClientInventoryCapture.capture` | Bounded 16 jobs, batches 1–4 |
| Render | `RenderTickEvent.END`, `GuiGraphics.renderItem` | Actual Minecraft ItemStack rendering, one job per render tick |
| Capture | Transparent `TextureTarget`, `NativeImage.downloadTexture`, vertical flip | Actual PNGs inspected; nonempty alpha pixels verified |
| Encode | Bounded PNG worker, not socket or FX thread | Existing implementation retained |
| Desktop decode/cache | `InventoryPreviewProvider`, `ItemPreviewCache` | Native result distinguished from static approximation |
| Display | `ResourcePreview` in Resource Browser, ingredient picker and slots | Browser card and detail both receive native PNGs |
| Application connection | `RecipeEditorApplication.start` attaches the shared provider to Companion | Existing wiring was already present |

This was not a missing ItemRenderer implementation. A registry snapshot contains runtime identities/metadata, not a live renderer or PNGs. Merely loading a snapshot cannot supply native icons. A running client, authenticated connection and advertised capability are required; a dedicated server cannot provide this capability.

## Reproduced defects

1. `completeStatic` stored a successful static fallback using the active native session context. After a temporary native failure, the next request could return that approximation from the native cache without retrying Minecraft. `ClientPreviewRecoveryTest` fails with the old cache write (`STATIC_ITEM_MODEL_2D` instead of `MINECRAFT_CLIENT_INVENTORY`) and passes with the fix. Static successes now use only the offline cache key.
2. Completed requests remained briefly in the pending map. An immediate retry could join the already completed fallback. Completed entries are removed before coalescing a new request; old batch cleanup uses entry identity.
3. Connection refresh depended on a poll on the same worker as potentially slow preview work. Mounted placeholders could remain stale. The provider now subscribes to actual connection events, invalidates immediately and uses a generation in pending keys to reject results from old connections.
4. Static model failure could obscure the native availability/failure reason. Diagnostics now retain both. Registry confirmation, client connection/capability and image availability are shown separately.

The historical screenshot alone cannot establish which connection state occurred in that particular session. The cache defect and recovery behavior were reproduced independently; no claim is made that all historical question marks shared one cause.

## Scope and safety

No new static texture resolver or manual Botania renderer. Forge rendering and protocol format are unchanged. The installed and built Companion JAR matched SHA-256 `3ceb19bca6b4979292f12d2dc177c72517e305edfddf28656d837861fb4e6eaa`. Default-stack previews do not prove every NBT variant, animation frame or modded renderer. Tests using generated fixture PNGs are explicitly marked synthetic and are separate from real Minecraft evidence.

The supplied Phase 21 attachment ends abruptly inside section 12. Only the supplied requirements were available; no unseen publishing instructions were assumed.
