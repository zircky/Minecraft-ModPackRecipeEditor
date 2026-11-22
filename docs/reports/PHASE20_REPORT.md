# Phase 20 — Windows EXE distribution

## Git and scope

Branch: `build/phase-20-windows-exe-distribution`, based on completed Phase 19 `9b503bb` (not merged into origin/master at fetch). Initial worktree clean. No product functionality, project format, Companion/protocol version or desktop version was changed. Actual desktop version: **1.1.3-alpha.1**. No merge/tag/GitHub Release or push was performed.

## Audit and implementation

The existing `gradle/windows-packaging.gradle.kts` already implements jlink, jpackage app-image and WiX EXE/MSI packaging; no second build pipeline was introduced. Classpath application, no module-info. Main class is the existing JavaFX wrapper `dev.recipeeditor.app.Launcher`. Desktop toolchain is Java 21. JavaFX 21.0.6 Windows controls/graphics/base and native libraries come from Gradle; FXML/media/web are not used. CSS, build info, icons and logback remain jar resources. ApplicationDirectories/Launcher already place mutable data in user locations, independent of working directory.

New root tasks `packageWindowsApp`, `packageWindowsInstaller`, `packageWindowsAll` reuse that pipeline and stage requested outputs. Packaging now validates actual staged resources and JavaFX DLLs. Missing WiX gets a specific diagnostic while portable packaging remains independent. Existing ICO/vendor Zircky and existing per-user Start menu/uninstall configuration are reused. System numeric version is **1.1.3**, full alpha version retained in UI, logs and setup filename. Companion remains separate; no Minecraft/Forge runtime, user modpacks or snapshots were bundled.

JDK/jpackage: **Eclipse Temurin 21.0.10+7 / jpackage 21.0.10**. Existing local WiX 3.14 binaries were used; no installers/tools were downloaded. Upstream [jpackage 21 documentation](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html) and OpenJDK jdk21u MsiWrapper.cpp were consulted; the EXE forwards command-line arguments to its embedded MSI.

## Checks

- `clean build`: **BUILD SUCCESSFUL**, 3m13s. 364 unit tests (2 environment symbolic-link skips), 98 integration tests, zero failures/errors; 12-module architecture gate passes.
- Separate `test`: **BUILD SUCCESSFUL**, 17s, up-to-date after clean build.
- `packageWindowsAll -PwixHome=<existing local WiX directory>`: **BUILD SUCCESSFUL**, 1m34s. Both portable app-image and EXE installer were created.
- `Test-WindowsDistribution.ps1`: passed actual PE/resource/version checks and invoked the real bundled Java 21 runtime. It deliberately distinguishes package validation from GUI/install validation.
- Portable GUI launched with JAVA_HOME/JDK_HOME/JRE_HOME removed and child PATH containing only Windows System32, from `.manual-tests/phase20/work`, with isolated MRE_DATA_HOME. Main window and About show **1.1.3-alpha.1** and Java **21.0.10**.
- Settings opened (existing placeholder, preferences not implemented by this packaging task). Real saved ISG test project opened via native file dialog; imported/indexed 14,908 resources and 11,490 recipes. Items displayed real icons; Recipe Browser and Export Wizard opened. Wizard cancelled without export, project remained unchanged. Logging recorded startup/import/index/shutdown outside the package. Window closed normally and process exit was verified.

## Actual outputs

Portable EXE:
`L:\My Add\RecipeEditor\build\distributions\windows\portable\MinecraftRecipeEditor\MinecraftRecipeEditor.exe`

Windows Installer:
`L:\My Add\RecipeEditor\build\distributions\windows\installer\MinecraftRecipeEditor-1.1.3-alpha.1-setup.exe`

Installer size: **63,289,856 bytes**. SHA-256: `d6d9744acf26d3c9c6a837f9ec15b38bc2fb231c6e15d301b3385ce59d1446c0`.

Distribute the entire portable folder with app/ and runtime/. The EXE alone is not standalone. User Java/Gradle/IDE are unnecessary to run it.

## Installer smoke status

EXE installer is built and structurally verified. Installation was guarded before invocation because an existing per-user **MinecraftRecipeEditor 1.0.0** is registered at `L:\test\MinecraftRecipeEditor-1.0.0-alpha.1\` (ProductCode `{8D445974-A6E8-3D31-AB59-B6363783A76B}`). The new alpha installer shares the upgrade channel and may replace it. Approval to upgrade/delete that existing test installation was requested; no existing installation was replaced or removed while awaiting an answer. Installation/Start menu/installed launch/uninstall are therefore not yet marked verified.

## Limitations and reproduction

Unsigned evaluation alpha; no certificate or Windows 10/clean-VM compatibility matrix was tested. Current GUI test ran on Windows 11 amd64 with only the child's Java search environment removed; system JDKs were not uninstalled. No fabricated installation/uninstallation success is claimed.

[Build/install instructions](../WINDOWS_BUILD.md). Raw logs and isolated user-data are under ignored `.manual-tests/phase20/`. Structural verification result is `build/distributions/windows/package-verification.json`. Build outputs are not committed. See `git log` for logical commits and final response for their SHAs.
