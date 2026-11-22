# Проверки проекта

Все команды выполняются из корня репозитория в Windows PowerShell, JDK 21 и JDK 17 должны быть доступны Gradle toolchains. Используется wrapper; Forge build остаётся отдельным.

## Обязательные проверки без Minecraft

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat verifyArchitecture
.\gradlew.bat testReport
.\scripts\verification\Summarize-TestResults.ps1
```

- `test` — unit/component regression tests, исключает JUnit tags `integration`, `external`, `performance`.
- `integrationTest` — только `integration`, исключает external/performance. Fixtures создаются в JUnit TempDir, реальная установка Minecraft не нужна.
- `build`/`check` включают bounded integration suite и Gradle architecture gate; root `build` также формирует сводный HTML. `testReport` запускает обе обязательные suites и объединяет результаты.
- Gradle `verifyArchitecture` проверяет фактические production project dependencies на cycles и зависимости business → UI/app. `ArchitectureBoundaryTest` проверяет compiled production constant pools на запрещённые UI/game references и Java 17 bytecode protocol/server. Это защита от регрессий границ, не универсальное доказательство SRP/thread safety.
- XML/HTML отдельного модуля: `<module>/build/test-results/<suite>/`, `<module>/build/reports/tests/<suite>/index.html`. Сводный HTML: `build/reports/tests/all/index.html`. PowerShell summary: `build/reports/tests/summary.json`, без пользовательских абсолютных путей/stack traces/tokens.
- Нулевое количество тестов в suite отдельного модуля допустимо: не каждый модуль имеет integration tests. Проверяйте общий отчёт, а не только exit code пустой suite.

`app` использует встроенный `java-test-fixtures`. `SyntheticModpack` — небольшой deterministic data-only JAR, 3 synthetic mod metadata entries, 3 recipes, item models и tag. Это не runnable Forge modpack. Fixture не входит в production JAR/distribution. Существующие локальные unit fixtures сохранены; переносить их все в общий модуль не требуется.

Новый lifecycle test проверяет UPDATE/DELETE/CREATE, Undo/Redo, повторный import/index, сохранённый ChangeSet и raw unknown JSON, анализ effective state и generation файлов трёх форматов. Datapack DELETE явно отклоняется. CraftTweaker проверяет поддержанный CREATE; KubeJS — CREATE/UPDATE/DELETE. Неподдержанный opaque recipe сохраняется, но блокирует свой экспорт. Проверяется, что несохранённая правка не попадает в reopened project/export.

Failure regressions проверяют source drift/quarantine, corrupt primary + explicit backup load, отсутствие перезаписи backup/primary, stale preview + user output + отсутствие manifest commit. Эти проверки не моделируют физическое отключение питания, disk-full или реальные ACL. Localhost integration тестирует client/server/protocol с synthetic SnapshotProvider, включая authentication, cancel, reconnect, connection loss и generation change. Игровой Forge API этим не проверяется.

## Проверки с внешней средой

Они исключены из обычного `build` независимо от свойств. Перенесённые из прежнего `test` opt-in проверки запускаются теперь через `externalTest`:

```powershell
.\gradlew.bat :app:externalTest '-PrealModpack=G:\PrismLauncher\instances\<instance>'
.\gradlew.bat :modpack-parser:externalTest '-PrealModpack=G:\PrismLauncher\instances\<instance>'
.\gradlew.bat :recipe-analyzer:externalTest '-PanalyzerSnapshot=L:\My Add\RecipeEditor\.manual-tests\phase7\evidence\modified.mresnapshot'
.\gradlew.bat :companion-client:externalTest --tests '*RuntimeLiveIntegrationTest' '-PruntimeSessionFile=<absolute-path-to-session.json>'
```

Замените placeholders фактическими путями. Список обязательных свойств/изолированного Minecraft окружения для старых runtime probes см. [PHASE6_REPORT.md](docs/reports/PHASE6_REPORT.md) и [PHASE7_REPORT.md](docs/reports/PHASE7_REPORT.md); их примеры `:companion-client:test` нужно заменить на `:companion-client:externalTest`. Без требуемых свойств opt-in tests SKIPPED — это не успешная runtime verification. Не публикуйте содержимое session.json. Успешная генерация export scripts не означает RUNTIME_VERIFIED.

## Нагрузка и профилирование

```powershell
.\gradlew.bat :recipe-analyzer:performanceTest
```

Задача существует и отдельно запускает существующую синтетическую 60000-recipe stress probe (tag `performance`, `analyzerStress=true`). Она исключена из build/check/test/integrationTest, результат сохраняется в `recipe-analyzer/build/phase8-stress-result.txt`. Это прежний smoke/stress сценарий, не JMH и не реализация всей Phase 9 Step 3. Его одиночные времена не являются надёжным microbenchmark. Полный performanceTest root агрегирует только уже размеченные performance tests.

Performance targets 500+ real mods / 100000+ recipes / 200000 resources / search P95 / 2 GiB heap ещё не подтверждены. JFR, retained objects/GC, FX responsiveness и cold/warm benchmarks выполняются отдельным Step 3. Не используйте успешную функциональную сборку как доказательство выполнения этих целей.

## Запуск приложения

```powershell
.\gradlew.bat :app:run
```

Эта команда запускает существующий UI. Новые тесты Phase 9 Step 2 работают на сервисном уровне и не считаются GUI E2E. Вся Phase 9 и Release 1.0 остаются NOT READY до последующих проверок и устранения release blockers.
