# Windows build and installation

The desktop version comes from `appVersion` in `gradle.properties` (currently **1.1.3-alpha.1**). Java is **not required on the user's machine**: both distributions bundle Java 21 and JavaFX. Copy the entire portable folder, not just its EXE.

## Build prerequisites

Windows x64, JDK 21 with jlink/jpackage (verified Temurin 21.0.10+7), Gradle wrapper and resolved Gradle dependencies. The application is classpath based (no module-info); actual launcher is `dev.recipeeditor.app.Launcher`. JavaFX controls/graphics/base 21.0.6 Windows jars and native DLLs are resolved from Gradle. No FXML/media/web modules are used.

Installer creation also requires compatible WiX 3 candle.exe and light.exe. Provide `-PwixHome=<directory containing both tools>`, WIX, or PATH. WiX is not downloaded automatically. Without WiX, build the portable distribution; installer tasks fail with a specific diagnostic. See [jpackage 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html) and [WiX 3.14.1](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm).

```powershell
java -version
jpackage --version
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat packageWindowsApp
.\gradlew.bat packageWindowsInstaller '-PwixHome=C:\Tools\wix314'
.\gradlew.bat packageWindowsAll '-PwixHome=C:\Tools\wix314'
.\scripts\verification\Test-WindowsDistribution.ps1
```

The new public tasks reuse the existing `:app:packageWindows`, jlink runtime and `:app:createInstaller` configuration. They do not call clean. Packaging depends on existing build/test/architecture gates and checks CSS, build metadata, logging, icons and JavaFX Windows DLLs before jpackage. Old Phase 10 task names remain compatible.

## Outputs

```text
build/distributions/windows/
  portable/MinecraftRecipeEditor/
    MinecraftRecipeEditor.exe
    app/
    runtime/
  installer/MinecraftRecipeEditor-1.1.3-alpha.1-setup.exe
```

The actual filename is derived from Gradle; update the example if the project version changes. Start the portable EXE via Explorer, a shortcut or PowerShell from any working directory. To install, run the setup EXE, choose a directory and shortcuts, and finish installation. The per-user installer provides a Start menu entry and Windows uninstall registration; remove it through Windows Installed Apps. Mutable settings/projects/logs are not installer payload and are not written into Program Files. Installation/uninstallation verification results are in [Phase 20 report](reports/PHASE20_REPORT.md).

Windows native ProductVersion is **1.1.3**, derived from the full version because native installer metadata is numeric. UI/About, logs and artifact name retain **1.1.3-alpha.1**. Vendor: Zircky; existing project ICO is reused. Packages remain unsigned evaluation alpha builds; no signing certificate, clean Windows VM matrix or stable-release claim is implied.

The runtime retains java.se, unsupported desktop APIs, EC crypto, charset/locale and zipfs support. JavaFX remains on the packaged classpath. Application data defaults to `%APPDATA%/MinecraftRecipeEditor` and `%LOCALAPPDATA%/MinecraftRecipeEditor`; projects use chosen locations. `MRE_DATA_HOME` can isolate developer smoke data. The verification script checks package contents and the real bundled runtime; actual GUI/install checks are separate.

Companion remains a separate Minecraft/Forge Java 17 artifact. It is not bundled into the desktop installer or launched through the desktop Java 21 runtime. No user modpacks, snapshots or test worlds are included. No GitHub Release/tag/publication is performed by these tasks.
