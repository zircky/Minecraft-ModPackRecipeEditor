# Phase 10 — Step 1–2: Release Foundation / Windows Packaging

18 ноября 2026. Выполнены только Step 1–2. Локальная версия **1.0.0-alpha.1**. Стабильный Release 1.0.0: **NOT READY**. Публикация, git tags и установка Companion в пользовательскую сборку не выполнялись.

## Аудит и архитектура

До изменений создан [RELEASE_READINESS.md](RELEASE_READINESS.md): состояние предыдущих фаз, подтверждённый функциональный baseline, packaging blockers и незакрытые release gates. Изменения ограничены упаковкой, версиями, пользовательскими каталогами и связанными проверками. Проект остаётся из 12 модулей; отдельный Forge build не включён в desktop multi-module configuration.

Desktop version задаётся **один раз** через `appVersion` в gradle.properties. Gradle проверяет SemVer; generated build-info.properties используется приложением, минимальным About, диагностическим текстом и логами. Companion version задаётся отдельным `companionVersion`; из неё генерируются protocol/shared identity resource и Forge mods.toml. Protocol=1 и project format=1 остаются независимыми схемами; конфигурация не допускает их фиктивного изменения без реализации совместимости. Project format не изменён.

`ApplicationDirectories` в core описывает roaming/local user paths без JavaFX и зависимости от cwd/install directory. Launcher подготавливает каталоги и log/cache/temp properties до инициализации сервисов/JavaFX. Runtime snapshots используют новый local path. Legacy snapshots переносятся только явной maintenance-командой с существующей проверкой schema/checksum; исходники не удаляются, существующие destinations не заменяются. Settings manager/автоматическая миграция settings не имитируются.

Production Logback: INFO по умолчанию, DEBUG/INFO/WARN/ERROR через system property, daily/10 MB rotation, 14 дней, archive cap 200 MB. Добавлен Open Logs Folder. Существующие import INFO содержат пути источников; полного сервиса redaction/crash diagnostics пока нет.

Gradle packaging script — `gradle/windows-packaging.gradle.kts`. Использует JDK 21 toolchain, jlink и jpackage без стороннего Gradle plugin. JavaFX Windows classpath/native JARs сохранены. Runtime содержит 27 JDK modules, не содержит javac/jpackage/headers/man pages; bin/server/jvm.dll и runtime/legal включены. Minecraft Companion собирается собственным wrapper на Java 17, независимо от bundled desktop runtime.

Оригинальная нейтральная grid icon создана в репозитории: ICO и PNG 16/32/48/64/128/256. Воспроизводимый build-time PowerShell script, без чужого логотипа и без генератора внутри приложения. Иконка используется в окне, About, launcher/installer и ярлыках.

## Gradle задачи

| Task | Реальное действие |
|---|---|
| `packageWindows` | Root build gates → dependencies/notices staging → jlink Java 21 → Windows app-image |
| `createInstaller` | EXE installer через jpackage/WiX 3 |
| `createMsiInstaller` | MSI installer через jpackage/WiX 3 |
| `releaseManifest` | Оба installers, image ZIP, свежий Companion build, SHA256SUMS и JSON manifest |
| `releaseWindows` | Полный локальный alpha bundle с build/tests/architecture и отдельной Forge сборкой; без публикации |
| `:app:runtimeImage`, `:app:stageWindows`, `:app:dependencyNotices`, `:app:imageZip` | Вспомогательные задачи runtime/staging/notices/ZIP |

Stable packaging заблокирован readiness guard. Windows MSI ProductVersion числовой **1.0.0**, full SemVer **1.0.0-alpha.1** присутствует в filenames, build metadata/UI/logs/manifest/description. Это alpha, не стабильный релиз. Alpha upgrade UUID отделён от будущего stable channel; upgrade/downgrade matrix ещё не проверена.

Installers настроены per-user, с Start menu group, directory chooser, Desktop shortcut и shortcut prompt. После статической проверки исправлена конфигурация Desktop shortcut: одного prompt flag было недостаточно, добавлен сам shortcut. MSI database содержит оба ярлыка, ProductVersion=1.0.0, Manufacturer=Zircky, ALLUSERS отсутствует (per-user package). Это статическая проверка конфигурации, не выполненная установка.

## Артефакты

Application Image: `app/build/windows/image/MinecraftRecipeEditor/`.
Launcher: `app/build/windows/image/MinecraftRecipeEditor/MinecraftRecipeEditor.exe`.
Размер всего Image: **79 848 210 bytes**. Для переносимого запуска нужен весь каталог или полный image ZIP, не один EXE.

Installers находятся в `app/build/windows/installers/`. Готовый локальный alpha bundle: **`app/build/windows/artifacts/1.0.0-alpha.1/`**.

| Файл в bundle | Размер, bytes | SHA-256 |
|---|---:|---|
| MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.exe | 62 970 368 | `3d1347ba975b482951142ca23cc6f1e4e3661c0194044ae3375636df5748efee` |
| MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.msi | 62 394 354 | `94fc2bd101b43106e2a4ed431440f8b5cf212efca24bd05766a1e12f3f74514c` |
| MinecraftRecipeEditor-1.0.0-alpha.1-windows-x64.zip | 61 103 330 | `833f3e3b8a5255ab1d039deb4282a515b7632838221a1fc4994f1d15ad710c03` |
| mre_companion-forge-1.20.1-0.1.0.jar | 79 510 | `c3af2c26063f68df4e311de6bd53fd59c3cb7306379f1dc967c54dde1a2a92a3` |

Отдельный Companion JAR: `minecraft-companions/forge-1.20.1/build/libs/mre_companion-forge-1.20.1-0.1.0.jar`. Проверены 40 classes, все major=61 (Java 17), test-only GameTests/ClientIntegrationHooks отсутствуют. Generated identity и mods.toml согласованы: 0.1.0. Forge `test` — NO-SOURCE, это не пройденные Minecraft GameTests.

Manifest содержит фактические desktop/native/protocol/project/Companion versions, environment/range, build timestamp, artifact sizes/hashes, `signed=false`, `releaseStatus=NOT READY`. Все четыре hashes повторно проверены независимым `Verify-ReleaseArtifacts.ps1`. ZIP проверен: bundled JVM и legal resources есть, test-fixtures/JDK compiler отсутствуют. WiX/JavaFX SDK tools ZIPs не включены в продукт.

## Сборка и тесты

| Проверка | Результат |
|---|---|
| `gradlew.bat clean build` | **BUILD SUCCESSFUL**, 1m 22s, 82 executed tasks |
| `gradlew.bat test` | **BUILD SUCCESSFUL**, 1s, 53 tasks up-to-date после clean build |
| Финальный `releaseWindows -PwixHome=...` | **BUILD SUCCESSFUL**, 1m 59s, 8 executed / 70 up-to-date |
| Отдельный Forge `clean build` внутри pipeline | **BUILD SUCCESSFUL**, 37s; Java 17, deprecated FML API warning |
| `gradlew.bat :app:run` | Старт 1.0.0-alpha.1 подтверждён INFO в 03:18:18; окно оставлено открытым |

JUnit: **269 tests: 267 passed, 2 skipped, 0 failures/errors**. Unit/component: 234 passed, 2 skipped. Reproducible integration: 33 passed. Два skips — создание symlink не разрешено Windows. External/live/stress suites в обычный build не входят и здесь не объявляются пройденными.

Добавлены 8 cases: ApplicationDirectoriesTest (4), ReleaseMetadataTest (2), LegacySnapshotMigrationTest (2). Проверены Windows paths/fallback/isolation/rejection relative paths, реальные generated versions и все icon sizes, validated migration/source retention/no overwrite/corrupt legacy rejection. Существующие save/reopen/export/graph/localhost regression suites проходят. Production dependency DAG и bytecode boundaries также проходят.

Предупреждение classpath JavaFX unnamed module остаётся; запуску не помешало. jlink предупреждает об устаревающем numeric compress=2, но runtime создаётся успешно. Подмены неуспешных тестов успехом нет.

## Фактическая проверка Application Image

На текущей Windows 11 amd64 EXE запущен из другого рабочего каталога; у дочернего процесса удалены JAVA_HOME/JDK_HOME, PATH ограничен Windows System32. В окне About наблюдались **1.0.0-alpha.1**, Java **21.0.10**, protocol=1, Companion=0.1.0, project format=1, NOT READY. Проверена загруженная JVM: **runtime/bin/server/jvm.dll внутри Image**. Нейтральная иконка и тёмный JavaFX UI отображаются. Данные/логи smoke run изолированы MRE_DATA_HOME вне Image. Это проверка запуска без Java в child environment, а не чистая VM без установленного JDK.

Во время интерактивного использования пользователем наблюдались успешный import/index и поиск: 71 mod definitions, 25 692 resources, 11 489 recipe definitions. Это свидетельство фактической работы packaged UI с данными, не контролируемый полный E2E/экспорт/runtime suite. Перед разрешённым пользователем закрытием проверены **0 Modified / 0 Created / 0 Deleted, Project matches saved snapshot**; приложение закрылось штатно. Несохранённые изменения не отбрасывались.

### Windows Installer verification

- EXE/MSI фактически созданы и проверены по size/hash.
- MSI database прочитана без установки: per-user configuration, numeric version, upgrade identity и оба shortcut definitions подтверждены.
- Install/uninstall, выбор shortcut в работающем installer, запуск из Start menu после установки, перезагрузка, settings/cache retention после удаления — **NOT EXECUTED**.
- Clean Windows без установленной Java и Windows 10 matrix — **NOT EXECUTED**.
- Неподписанные alpha artifacts не объявляются доверенным подписанным публичным продуктом.

Свидетельства: `.manual-tests/phase10/evidence/clean-build.log`, `test.log`, `release-windows.log`, `app-run.log`, `test-summary.json`, `smoke-result.txt`, `msi-properties.json`, `artifact-verification.log`. Сохраняются вне clean outputs.

## Поддержка и ограничения

Minecraft Companion: реализован **Minecraft 1.20.1 / Forge 47.x**, текущая сборка Forge **47.4.10**, Minecraft Java **17**. Исторические runtime tests Phase 6–7 относятся к конкретным проверенным средам, не ко всем 47.x. Новая Minecraft сессия в Phase 10 не запускалась. NeoForge/Fabric/Quilt Companion отсутствуют и не представлены готовыми.

Статическое чтение metadata нескольких загрузчиков не означает полноценную поддержку editing/export/runtime для них. Подтверждённые профили desktop export остаются version-scoped. GTCEu **7.1.4** поддержан частично; **8.0.0-SNAPSHOT** unsupported. Create/Mekanism adapters отсутствуют. Recipe Analyzer — только backend Step 1–3, без нового Dashboard/cost/energy/impact UI.

Не реализованы Phase 10 Step 3–8: Settings Manager, First Launch Wizard, Java detector/resolver, themes/localization, Companion manager/install wizard, updates, crash reporting/backup manager, полный About/licenses UI, CI/CD, полный user manual и final release matrix. Минимальный About добавлен для проверки actual build identity, не объявляется всем Step 6.

Исходные Phase 9 recovery/lifecycle/performance/security blockers остаются. Bundled runtime сам по себе не доказывает heap ≤2 GB или отсутствие leaks. Project format и export contracts не изменены.

Embedded dependency notices, дополнительные официальные license texts/OpenJFX legal resources и runtime legal включены. Первичные источники: [Oracle JDK 21 jpackage](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html), [WiX 3.14.1](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm); provenance license files — `packaging/licenses/SOURCES.md`. Distribution license desktop проекта, source/notice obligations и финальный dependency/security review остаются public-release gates. Репозиторий/remote/update URL не выдумывались; публикации нет.

## Итоговая таблица

| Component | Status | Tested | Release Ready |
|---|---|---|---|
| Desktop Application | Packaged alpha, bundled Java 21 | Unit/integration + Image UI/About smoke | NO — alpha only |
| Modpack Import | Реализован, статический индекс неполон | Regression + наблюдение packaged import/search | NO — final matrix incomplete |
| Recipe Editor | Overlay/history/save реализованы | Existing regression/lifecycle tests | NO — recovery/fault gates incomplete |
| Recipe Export | Datapack/KubeJS/CraftTweaker version-scoped | Generated files/transactions tests; no new game run | NO |
| Runtime Companion | Forge 1.20.1 JAR rebuilt, Java 17 | Build/bytecode/localhost tests; current Minecraft NOT EXECUTED | NO |
| GregTech Integration | Partial 7.1.4; GT8 unsupported | Existing fixtures; historical live verification only | NO |
| Create Integration | Не реализована | NOT EXECUTED | NO |
| Mekanism Integration | Не реализована | NOT EXECUTED | NO |
| Recipe Analyzer | Backend Step 1–3 | Unit/graph/effective-state tests | NO — remaining steps incomplete |
| Windows Installer | Actual unsigned EXE/MSI created | Hashes + static MSI structure; install/uninstall NOT EXECUTED | NO — alpha artifact only |

## Использование

Для локального запуска откройте `app/build/windows/image/MinecraftRecipeEditor/MinecraftRecipeEditor.exe` либо распакуйте весь image ZIP из bundle. Команды воспроизведения, prerequisite WiX, каталоги, migration и ограничения приведены в [WINDOWS_PACKAGING.md](WINDOWS_PACKAGING.md).

Проверить hashes:

```powershell
.\scripts\verification\Verify-ReleaseArtifacts.ps1 -Directory app/build/windows/artifacts/1.0.0-alpha.1
```

Изменённые файлы: [PHASE10_FILES.txt](PHASE10_FILES.txt). Работа остановлена после Step 1–2. Stable Release Ready не объявляется.
