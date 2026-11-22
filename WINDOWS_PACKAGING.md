> Historical Phase 10 notes. Current commands, paths and version: [docs/WINDOWS_BUILD.md](docs/WINDOWS_BUILD.md).

# Windows alpha packaging — Phase 10 Step 1–2

The current package is **1.0.0-alpha.1, NOT READY for stable release**. The application is not signed. Settings/wizard/updater/Companion installation/recovery are later steps. Packaging is not permission to publish, select the project's license or create Git tags.

## Build prerequisites

Windows x64, JDK 21 (desktop jlink/jpackage), JDK 17 (independent Forge Companion build). Both must be discoverable by Gradle toolchains. The Gradle daemon's Java may differ; packaging always selects a Java 21 toolchain. JavaFX Windows native JARs are already resolved by Gradle. No extra packaging plugin is required.

EXE/MSI creation additionally requires WiX 3 candle.exe/light.exe. Compatible build tooling: [official WiX 3.14.1 binaries](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm), ZIP SHA-256 `6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31`. Extract to a trusted local tools directory. Use `-PwixHome=<directory-containing-candle.exe-and-light.exe>` or WIX/PATH. WiX is only needed on the build machine and is not bundled. Downloads are not performed automatically by Gradle tasks.

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat packageWindows
.\gradlew.bat createInstaller '-PwixHome=L:\My Add\RecipeEditor\.manual-tests\phase10\tools\wix314'
.\gradlew.bat createMsiInstaller '-PwixHome=L:\My Add\RecipeEditor\.manual-tests\phase10\tools\wix314'
.\gradlew.bat releaseWindows '-PwixHome=L:\My Add\RecipeEditor\.manual-tests\phase10\tools\wix314'
```

The shown wixHome is the local verification setup; replace it on another machine. No developer absolute path is embedded in build configuration. Application Image needs no WiX. Native Windows packages cannot be cross-built on Linux. Tasks fail on missing tools or failed tests; they do not print a fictional success.

`packageWindows` runs root build gates and creates a jlink runtime + jpackage app-image. `createInstaller`/`createMsiInstaller` depend on it. `releaseWindows` also builds the Java 17 Companion with its own wrapper, creates an image ZIP and installers, collects Companion, generates SHA-256 and release-manifest.json. No network publication or git tag occurs. `releaseManifest` is also available as an explicit aggregate task. Unit/integration/architecture gates are inherited from Phase 9. External/live/stress suites remain opt-in.

## Output

```text
app/build/windows/
  runtime/                         jlink Java 21 image
  image/MinecraftRecipeEditor/
    MinecraftRecipeEditor.exe      native launcher
    app/                           app JARs, JavaFX Windows DLLs in JARs, legal notices
    runtime/                       bundled runtime, including module legal/
  installers/
    MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.exe
    MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.msi
  artifacts/1.0.0-alpha.1/
    MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.zip
    MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.exe
    MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.msi
    mre_companion-forge-1.20.1-0.1.0.jar
    SHA256SUMS.txt
    release-manifest.json
```

For the portable image, distribute/extract the **entire image ZIP**, then open MinecraftRecipeEditor.exe. Copying only EXE will not work. Bundled runtime needs no Gradle/IDE/system Java. Minecraft itself still needs Java 17; the desktop runtime is not a Minecraft launcher runtime.

Native installer ProductVersion is numeric **1.0.0** because Windows MSI version fields cannot carry SemVer prerelease labels. The full **1.0.0-alpha.1** is retained in filenames, generated build info, UI/About, logs, description and manifest; installers remain alpha. The alpha upgrade UUID is separate from any future stable channel; alpha-to-stable upgrade/downgrade strategy is not validated. Stable packaging is currently blocked by an explicit readiness guard.

Installers are configured per-user, with Start menu group Minecraft Recipe Editor Alpha, directory chooser and shortcut prompt. Default install scope does not require admin. The packages contain app/runtime only; projects, APPDATA and LOCALAPPDATA are outside the install directory, with no custom deletion action. Actual install/uninstall/reboot/shortcut behavior must still be verified in the final release validation step. No claim of completed Windows 10/11 matrix or clean VM test follows from a successful MSI build.

## Runtime and icon

The runtime uses `java.se,jdk.unsupported,jdk.unsupported.desktop,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.zipfs`. This preserves baseline APIs, desktop/ImageIO, XML, networking, management and locale/charset/crypto support without bundling javac/jpackage/headers/man pages. JavaFX stays on the existing classpath; native library extraction uses the user's local cache. Verify real workflows before reducing modules further. Specification: [Oracle JDK 21 jpackage](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).

Original neutral grid icon: `packaging/icons/MinecraftRecipeEditor.ico` (16/32/48/64/128/256) and matching app PNG resources. `packaging/Generate-Icon.ps1` reproduces it using System.Drawing, without a third-party logo or an in-app image generator. Window/About and installer/launcher use these assets. Extra Settings/themes/localization are not included.

## User data and migration

```text
%APPDATA%/MinecraftRecipeEditor/        settings.json, profiles/, ui-state.json (paths prepared)
%LOCALAPPDATA%/MinecraftRecipeEditor/   cache/, logs/, snapshots/, temp/
```

Settings/UI-state files are not created as fake settings: their manager is Step 3. Projects remain at user-selected locations. Logging defaults INFO, accepts `-Dmre.logLevel=DEBUG|INFO|WARN|ERROR`, rolls at 10 MB, retains 14 days with 200 MB archive cap. SizeAndTimeBasedRollingPolicy can briefly exceed a threshold by a log event/current active file. Open Logs Folder and minimal build/About information are available; complete update/license/diagnostics UI remains later work. The inherited INFO import messages still include source paths; no automatic crash-report export/redaction service is claimed.

Legacy `%USERPROFILE%/.minecraft-recipe-editor/runtime-snapshots` is preserved. Explicit maintenance invocation copies validated snapshots to the new location, without replacing existing destinations or moving/removing sources:

```powershell
& '.\app\build\windows\image\MinecraftRecipeEditor\MinecraftRecipeEditor.exe' --migrate-legacy-snapshots
```

Corrupt source snapshots are rejected using existing checksum/schema validation. At most 1000 candidates; errors abort that invocation and leave original data intact (prior successfully copied entries may remain). There is no automatic migration of logs, settings or projects; earlier versions had no implemented settings manager. Legacy logs remain available in the old directory.

For developer smoke tests only, an absolute `MRE_DATA_HOME` overrides data roots to `<override>/roaming` and `<override>/local`; it is not embedded in distribution config. This allows isolated tests without touching user settings. Log paths are configured before JavaFX/service logger initialization.

## Licensing and release gates

`dependencyNotices` extracts real license/notice resources from runtime dependency JARs. `packaging/licenses` adds missing upstream texts, including actual OpenJFX 21.0.6 module legal files; provenance and checksums are in SOURCES.md. The Java runtime's legal directory is retained. `DISTRIBUTION-NOTICE.txt` explicitly marks evaluation status. The owner's desktop distribution license, source availability/notice obligations and a final dependency/security review remain public-release gates. No Minecraft/GT/KubeJS modpack is bundled.

Final results, artifact sizes/checksums, smoke-test limitations and component readiness are in [PHASE10_REPORT.md](docs/reports/PHASE10_REPORT.md) and RELEASE_READINESS.md. App-image launch with Java removed from the child environment is a useful bundled-runtime check; it is not a claim that the machine had no JDK installed.
