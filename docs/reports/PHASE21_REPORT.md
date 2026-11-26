# Phase 21 — Runtime item previews

## Result

The native Minecraft pipeline was already implemented. This phase fixes fallback cache contamination, retry coalescing, delayed connection refresh and loss of native failure diagnostics. See [the audit](PHASE21_AUDIT.md) for the reproduced defects and executable path. No static resolver, Forge renderer replacement or protocol version change was introduced.

Runtime now exposes **Client Item Preview**, defaulting to `gtceu:aluminium_block`, with a native-only request button, decoded ImageView, connection/capability status and request/sent/transferred/failure counters. Diagnostic content scrolls instead of clipping. Disconnect clears its stale image/result. Resource Browser displays client availability independently of historical registry confirmation, and mounted resource previews refresh on connection events.

## Real Minecraft evidence

Tested the authorized **ISG-0.6.0-alpha.1-harsh** instance with Minecraft **1.20.1**, Forge **47.4.28**, Java **17**, and the existing client Companion **0.1.0**. Desktop uses Java **21**. No world was opened or changed; client inventory rendering worked at the main menu.

`ClientPreviewPipelineEvidenceTest` loads authentic saved registry evidence, then constructs a production Resource Browser **without static model assets**. It first verifies disconnected placeholders, connects to the actual Minecraft session, and checks card/detail native previews without rebuilding the first browser. It checks nonempty PNG alpha pixels, then disconnects and verifies explicit missing-preview state. Both ordinary/background and explicitly minimized-client runs passed.

| Actual registered item | Native source | Browser previews |
| --- | --- | --- |
| `gtceu:aluminium_block` | `MINECRAFT_CLIENT_INVENTORY` | Card + detail |
| `gtceu:aluminium_ingot` | `MINECRAFT_CLIENT_INVENTORY` | Card + detail |
| `gtceu:aluminium_dust` | `MINECRAFT_CLIENT_INVENTORY` | Card + detail |
| `gtceu:aluminium_plate` | `MINECRAFT_CLIENT_INVENTORY` | Card + detail |
| `gtceu:aluminium_ore` | `MINECRAFT_CLIENT_INVENTORY` | Card + detail |
| `botania:floating_daffomill` | `MINECRAFT_CLIENT_INVENTORY` | Card + detail |

Each run: **12 requested, 12 sent, 12 PNG transferred, 0 failed**. All six IDs were present in the supplied real snapshot. [Metrics](phase21-evidence/metrics.json), [transport](phase21-evidence/transport.txt), [aluminium block](phase21-evidence/gtceu_aluminium_block.png), [Botania](phase21-evidence/botania_floating_daffomill.png).

The rebuilt portable was also launched and controlled through the [Computer Use skill](C:/Users/ziran/.codex/plugins/cache/openai-bundled/computer-use/26.1117.41037/skills/computer-use/SKILL.md). With Java environment variables removed and child PATH restricted to Windows System32, its bundled runtime opened version **1.1.3-alpha.1**. Runtime authenticated the actual minimized Minecraft client, advertised `item-previews-v1`, and the request button displayed both the aluminium block and Botania icon. Counters reached **2 sent / 2 transferred / 0 failed**. A request after disconnect showed `CLIENT_DISCONNECTED`. The later diagnostic scrolling/stale-result correction is additionally covered by production-view TCP tests and final portable UI verification.

## Automated coverage

- `ClientPreviewRecoveryTest`: successful static fallback cannot poison the next native request; actual authenticated TCP connection refreshes an existing production ResourcePreview/ImageView.
- `RuntimePreviewProbeViewTest`: real request button, transferred PNG, disconnect clears stale image; connected server without client capability cannot report a rendered image.
- `InventoryPreviewInvalidationTest`: existing snapshot/session/resource-change isolation retained. The eventless fake explicitly publishes its disconnect transition; the diagnostic must retain both native unavailability and `MISSING_MODEL`.
- `ClientPreviewPipelineEvidenceTest`: opt-in **actual Minecraft**, separate from synthetic fixture tests. Requires `-PrealModpack`, `-Pphase19Snapshot`, `-PpreviewSession`; session credentials are read privately and never printed.

Synthetic fixture PNGs verify transport/UI behavior only. They are not substituted for the real-client results above. Ingredient picker/recipe-slot shared pipeline remains covered by existing integration tests; those surfaces were not separately exercised against Minecraft in this phase.

## Verification and distribution

- Final `clean build`: **BUILD SUCCESSFUL**, 3m05s; 12-module architecture gate passes, no cycles.
- **364 unit tests**, zero failures/errors, two existing environment-dependent symbolic-link skips; **102 integration tests**, zero failures/errors/skips.
- Separate `test packageWindowsAll`: **BUILD SUCCESSFUL**, 1m24s, including the rebuilt portable and EXE installer.
- `scripts/verification/Test-WindowsDistribution.ps1`: passed PE/resource/JavaFX native/version checks and executed the bundled Java **21.0.10**. Its structural JSON deliberately leaves GUI verification separate.
- Final portable GUI launched again after the clean build: version **1.1.3-alpha.1**, Runtime disconnected state and request refusal correct, full diagnostic text readable in the scrollable tab. Closed normally. The disconnect-clears-image correction also passed the authenticated TCP production-view test in the final clean build.
- Real-client external probe passed twice, with normal/background and explicitly minimized Minecraft; a new JVM/empty native cache was used for each run.

Raw logs, regression failure with the old cache rule, both real-client runs and isolated portable data are in ignored `.manual-tests/phase21/`.

Portable: `L:\My Add\RecipeEditor\build\distributions\windows\portable\MinecraftRecipeEditor\MinecraftRecipeEditor.exe`.

Installer: `L:\My Add\RecipeEditor\build\distributions\windows\installer\MinecraftRecipeEditor-1.1.3-alpha.1-setup.exe`.

Installer SHA-256: `0210ad7741b4dbe3b90689505a135f3041319794cdbe4a9f61fa44ad72fa97d5`.

Distribute the entire portable folder, including `app` and `runtime`. To obtain native icons, launch this Minecraft instance, choose its `minecraft/config/mre_companion/session.json` in **Runtime**, and **Connect**. A cached snapshot provides registry evidence; it does not replace that connection. Native-only diagnosis: **Runtime → Client Item Preview → Request client preview**.

No version changes, merge, tag, push or public release. The existing installed 1.0 application was not replaced. No claim is made that every historical question mark, arbitrary mod or NBT variant has been reproduced.

Implementation commits: `6a94fc0` (Companion diagnostics), `e99c7fd` (cache/recovery/UI), `c89c9f4` (readable/current probe result), `e98e23d` (regression and actual-client probes). Final documentation/evidence is a separate commit; see Git history.
