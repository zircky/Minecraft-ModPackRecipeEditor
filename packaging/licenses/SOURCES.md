# Supplemental upstream license resources

These are unmodified upstream notices/licenses for the actual dependency versions. No ownership of third-party code is claimed. Embedded JAR notices are collected separately by dependencyNotices; this directory supplies resources missing from binary JARs.

| Files | Source |
|---|---|
| gson-2.10.1-LICENSE | https://raw.githubusercontent.com/google/gson/gson-parent-2.10.1/LICENSE |
| antlr4-4.11.1-LICENSE.txt | https://raw.githubusercontent.com/antlr/antlr4/4.11.1/LICENSE.txt |
| tomlj-1.1.1-LICENSE | https://raw.githubusercontent.com/tomlj/tomlj/1.1.1/LICENSE |
| logback-1.5.16-LICENSE.txt | https://raw.githubusercontent.com/qos-ch/logback/v_1.5.16/LICENSE.txt |
| logback-EPL-1.0.html | https://www.eclipse.org/legal/epl-v10.html (full license referenced by Logback notice) |
| openjfx-21.0.6/{javafx.base,javafx.controls,javafx.graphics} | legal/ from https://download2.gluonhq.com/openjfx/21.0.6/openjfx-21.0.6_windows-x64_bin-sdk.zip |

OpenJFX SDK ZIP SHA-256: `47a9048dcc16003919b117e916ae53039e6094aadc41ef4f560800d7054b3f95`.
OpenJFX includes GPLv2, Classpath exception details, assembly exception and graphics native-library notices. Retain all extracted files. The jlink Java runtime retains its own complete module legal/ directory. Source locations: https://github.com/openjdk/jfx21u and the Eclipse Temurin release identified by the bundled runtime's release file (https://github.com/adoptium/temurin21-binaries).

Jackson, SLF4J and Checker Framework notices are extracted from their JARs. Logback is dual-licensed; the full EPL text accompanies its upstream choice-of-license notice. This inventory is not a claim of completed public-release legal review. The desktop project's distribution license and a public source-distribution strategy still require the owner's decision before publication. No public release is made by these Gradle tasks.
