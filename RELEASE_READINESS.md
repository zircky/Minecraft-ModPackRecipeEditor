# Release Readiness — initial audit, Phase 10 Step 1–2

18 November 2026. Status: **NOT READY for stable 1.0.0**. Packaging is a prerequisite, not proof that all product/reliability gates have passed. The development package uses **1.0.0-alpha.1**; target stable version is 1.0.0.

Reviewed: 12-module Gradle configuration, separate Forge build, Launcher/JavaFX entry point, current user paths/logback, project persistence/backups, export transaction/manifest, Companion/protocol, previous phase reports and Phase 9 JUnit results. Initial functional baseline: 228 unit/component + 31 integration passed, two Windows symlink tests skipped, no failures. This is the previous verified baseline, not a new live Minecraft run.

## Existing release blockers

- Phase 7 includes only Step 1–2. Partial GTCEu 7.1.4 profile is verified historically; GT8 SNAPSHOT is unsupported. Create/Mekanism adapters are absent.
- Phase 8 includes only Step 1–3: backend framework/conflicts/graph. Production planning/cost/energy/impact/dashboard are absent.
- Phase 9 includes only Step 1–2: audit/tests. Crash-session recovery, profiling, retention/GC, FX responsiveness, security/fault campaigns, current Minecraft/platform matrix and final release gates are incomplete. Queued-future shutdown lifecycle risk remains open.
- Full settings/wizard/themes/localization, Companion manager/installation wizard, updater, crash reports, managed backups and complete About/licenses UI are later Phase 10 steps. No placeholder is to be described as implemented.
- No configured Git repository/remote, release source URL or project distribution license was found. No GitHub publication/tag/CI provisioning is authorized by this request. A project license must be selected by its owner before public distribution; third-party notices/runtime licenses must accompany packaging.

## Packaging blockers addressed by this request

- Desktop version was hardcoded 0.1.0-SNAPSHOT separately in Gradle and Java.
- No jpackage/jlink/installer tasks or runtime image. JDK 21 and 17 exist locally; WiX not on PATH initially.
- Logs/snapshots used user.home/.minecraft-recipe-editor. Standard roaming/local Windows directories and bounded production logs are needed.
- No project-owned multi-resolution Windows icon, release manifest/checksums or packaging documentation.

## Planned boundaries

Only Release Foundation and Windows Packaging. Keep the existing classpath launcher and JavaFX dependencies; bundle Java 21 independently of Minecraft Java 17. Use generated metadata and one source of desktop version. Produce actual app-image/EXE/MSI where toolchain permits. Packages remain unsigned alpha artifacts; do not publish automatically. Package build metadata includes separate desktop/protocol/Companion/project-format versions, prerelease state and NOT READY status.

Installer configuration: current-user install, stable upgrade identity for the alpha channel, Start menu entry, optional shortcut prompt, no project deletion. Application image smoke test can hide JAVA_HOME/PATH for the child process; this is not the same as a clean Windows VM without any JDK installed. MSI/EXE creation is separate from install/uninstall and reboot/platform-matrix verification. Record exact outcomes in [PHASE10_REPORT.md](docs/reports/PHASE10_REPORT.md).

Primary packaging specification: [Oracle JDK 21 jpackage](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html). For JDK 21 native Windows installers use the compatible [WiX 3.14.1 binary release](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm), build-machine tooling only, not shipped in the app. Source inspection and packaged dependency licenses will be documented; public licensing readiness is not inferred from successful packaging.
