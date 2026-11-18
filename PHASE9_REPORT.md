# Phase 9 — Step 1–2: результаты

Дата: 18 ноября 2026. Выполнены Technical Audit и Test Infrastructure. Step 3–6 и Phase 10 не начаты. Phase 9 целиком не завершена; Release 1.0 — **NOT READY**.

## Изменения

Исходный технический отчёт составлен до изменений: [PHASE9_AUDIT.md](PHASE9_AUDIT.md). Приоритеты CRITICAL/HIGH/MEDIUM/LOW, подтверждения и риски разделены. Изучены существующие 12 модулей, независимый Forge build, тесты и результаты предыдущих фаз. Изменённые/созданные файлы перечислены в [PHASE9_FILES.txt](PHASE9_FILES.txt).

Production Java-код не изменён. Не добавлены новые модули, сторонние библиотеки, дублирующие сервисы, фиктивные данные в продуктовый UI или недостающие возможности старых фаз. Применён встроенный Gradle java-test-fixtures только в app; проверено отсутствие SyntheticModpack в app JAR и test-fixtures JAR в application ZIP.

- Разделены Gradle suites `test`, `integrationTest`, `externalTest`, `performanceTest` посредством JUnit tags. В обычный build входят unit/component и bounded integration tests. Live/external и stress изолированы независимо от переданных opt-in properties.
- Добавлены root aggregate задачи, сводный HTML TestReport, отдельные module XML/HTML и PowerShell JSON summary. Gradle учитывает module-specific test properties при запуске дополнительных suites.
- Добавлен architecture gate по фактическим Gradle project dependencies: cycles, core dependencies, business → UI/app и прямые запрещённые JavaFX/Minecraft dependencies. Bytecode regression test проверяет production references и Java 17 major version для protocol/server. Полной проверки SRP/всех транзитивных библиотек это не обещает.
- Добавлена deterministic SyntheticModpack: data-only JAR, synthetic metadata, models, tag, fully parsed и opaque recipes. Все экземпляры находятся в TempDir. Реальные пользовательские сборки не изменяются.

## Новые проверки

**9 новых JUnit cases**: 2 architecture, 4 lifecycle (включая 3 export parameter cases), 2 failure regressions, 1 runtime generation test. Существующие тесты сохранены и размечены по suites.

`ProjectLifecycleIntegrationTest` выполняет import → installed metadata → resource/recipe discovery → registry → UPDATE/DELETE/CREATE → Undo/Redo → async save → новый import/index → reopen/materialize/restore → analyzer/graph/reverse index → dry-run/export/files/manifest. Проверяет кириллицу/пробелы в путях, original ID и references, saved vs unsaved changes, unknown nested Unicode JSON, quarantine после изменения source fixture и отсутствие любых побочных изменений JAR. Неподдержанный opaque recipe сохраняется, его экспорт отклоняется.

Проверенные операции экспорта: Datapack CREATE/UPDATE с явным отказом DELETE, KubeJS CREATE/UPDATE/DELETE, CraftTweaker поддержанный CREATE. Сравниваются выбранные recipe IDs, сохранённое cooking time, содержимое файлов и manifest `GENERATED_RUNTIME_UNVERIFIED`. Это генерация/валидация файлов, не запуск scripts в Minecraft.

`ProjectFailureRegressionTest`: повреждённый primary не заменяется; корректный `.bak` не повреждается; backup читается явно без автоматической перезаписи primary; временный файл после неудачного save очищается. При создании пользовательского файла после export preview возвращается FAILED, файл сохраняется, другие outputs и manifest не создаются.

`RuntimeGenerationIntegrationTest`: настоящий TCP localhost client/server с synthetic SnapshotProvider; смена generation между transfer datasets запрещает complete snapshot, следующая новая сессия успешно синхронизируется с актуальной generation. Существующие integration tests покрывают handshake/authentication, reconnect, cancel, connection loss и snapshot persistence. Forge/Minecraft API этим не проверяется.

Новая проверка architecture gate также выполнена с временным Gradle init script, добавляющим core → recipe-engine. Gate ожидаемо отклонил production cycle; исходные build files этим экспериментом не изменены. Намеренный FAILED log хранится отдельно в `architecture-negative.log`; после него штатный gate снова прошёл. Это не ошибка итоговой сборки.

## Результаты Gradle и JUnit

| Команда | Результат |
|---|---|
| `gradlew.bat clean build` | BUILD SUCCESSFUL, 59 s, 79 executed tasks; unit + integration + architecture + aggregate HTML |
| `gradlew.bat test` | BUILD SUCCESSFUL, 1 s, 50 up-to-date tasks после clean build |
| `gradlew.bat integrationTest testReport verifyArchitecture` | BUILD SUCCESSFUL, 1 s, 1 executed / 62 up-to-date tasks |
| `gradlew.bat :app:run` | Старт после создания главного окна подтверждён INFO log в 02:35:32; процесс оставлен работающим |

В логе старое предупреждение JavaFX unnamed module. Оно не помешало старту. Визуальные GUI-сценарии и E2E в этой фазе **NOT EXECUTED**. Новый GUI не добавлялся.

| Suite | Tests | Passed | Skipped | Failures / errors |
|---|---:|---:|---:|---:|
| Unit/component (`test`) | 230 | 228 | 2 | 0 / 0 |
| Reproducible integration | 31 | 31 | 0 | 0 / 0 |
| Всего обязательных | **261** | **259** | **2** | **0 / 0** |

Два skips — Windows не разрешила создание symlink в ModScannerTest и ExportFileWriterTest. Они не считаются пройденными security checks. External/performance tests исключены из обязательных suites, поэтому не включены в эти числа и не объявляются пройденными/пропущенными. Изменение общего числа относительно Phase 8 объясняется разделением suites и 9 новыми cases, а не удалением тестов.

Проверена изоляция по фактическому clean-build task log: `externalTest` и `performanceTest` не выполнялись. Повторный обязательный запуск был up-to-date; повторно исполнять неизменившийся полный набор без основания не требуется.

Источники результатов:

- `build/reports/tests/all/index.html` — сводный HTML, XML находятся в module/build/test-results/{test,integrationTest}.
- `build/reports/tests/summary.json` — воспроизводимый JSON summary.
- `.manual-tests/phase9/evidence/`: clean-build.log, test.log, final-verification.log, test-summary.json, architecture-negative.log, app-run.log; эти свидетельства сохраняются при clean.

## Ошибки, исправления и открытые риски

Исправлен инфраструктурный пробел **A08**: появились изолированные suites, общая lifecycle fixture, сквозные/regression проверки, architecture guard и aggregate reports. Прежние opt-in свойства сами по себе больше не запускают тяжёлые проверки в обычном test/build. Документирован переход старых `:module:test` opt-in команд к `:module:externalTest`.

Подтверждённых CRITICAL ошибок по аудиту нет. Production defects в Step 1–2 не исправлялись: задача ограничена аудитом и инфраструктурой. HIGH A01/A02 остаются release blockers (нет unsaved-session recovery, незавершённые функции). A03 — риск незавершённых queued futures/shutdown lifecycle, установленный по коду, без заявления воспроизведённой потери данных. Он требует отдельной проверки/исправления Step 4. Linear UI filtering, cache invalidation, unbounded service queues, log size caps и listeners требуют дальнейших измерений/работ; подробности в аудите.

Сохранение и export safety имеют положительные functional regression results, но отсутствие disk-full/ACL/power-loss испытаний не позволяет объявить абсолютную crash safety. `.bak` и export journal не являются полной системой восстановления несохранённой сессии.

## Performance, память, profiling, runtime

В текущем запросе Step 3 **не выполнялся**. Новых performance measurements, peak/retained heap, GC/JFR/JMH/VisualVM результатов нет. Desktop `-Xmx2g` — настройка, не доказательство фактического потребления ≤2 GB. Отсутствие profiler результатов не заменяется утверждением об отсутствии memory leaks. Данные Phase 8 — исторические smoke/stress observations, здесь не выдаются за новый performance gate.

Minecraft/Forge, GT8 8.0.0-SNAPSHOT+e24b238, GT7.1.4 live reload и Create/Mekanism в Phase 9 **NOT EXECUTED**. Create/Mekanism adapters отсутствуют, GT8 специализированная совместимость не заявляется. Отдельный Forge Companion в этом запросе заново не собирался; protocol/server собраны root build и проверены как Java 17. Windows 10/11 matrix и запуск через IntelliJ не выполнены. Windows paths с кириллицей/пробелами проверены текущими integration tests.

## Component matrix

| Component | Tests | Performance | Stability | Status |
|---|---|---|---|---|
| Modpack Import | 41 passed, 1 symlink skipped + app lifecycle | NOT EXECUTED | Детерминированные import/reimport и source drift проходят; fault campaigns не выполнены | PARTIAL |
| Recipe Engine | 13 passed + editor tests в integrations и lifecycle | NOT EXECUTED | Overlay/history/persistence/quarantine проверены; crash recovery отсутствует | PARTIAL |
| Exporters | 53 unit passed, 1 symlink skipped, 3 integration + app lifecycle/failure | NOT EXECUTED | Stage/rollback/stale preview/unsupported guards; runtime application не проверено | PARTIAL |
| Runtime Companion | 8 client + 10 protocol + 10 localhost integration passed | NOT EXECUTED | Synthetic provider, cancellation/reload-generation guards; новый Minecraft run отсутствует | PARTIAL |
| Recipe Analyzer | 27 passed + app effective-state graph | NOT EXECUTED | Backend Step 1–3; cost/energy/impact/UI отложены | PARTIAL |
| JavaFX UI | 2 navigation tests; startup log подтверждён | NOT EXECUTED | GUI E2E, listeners/retention, responsiveness не проверены | NOT VERIFIED |

## Release gates и воспроизведение

Build/обязательные functional suites/production module DAG — PASS с двумя явно указанными platform skips. Profiling/performance/security campaigns/live Minecraft/platform matrix — NOT EXECUTED. Recovery и продуктовые функции — INCOMPLETE. Поэтому Release 1.0 **NOT READY** и вся Phase 9 не объявляется завершённой.

Команды, suite rules, opt-in свойства и ограничения fixtures находятся в [TESTING.md](TESTING.md). Выполнены только Step 1–2; следующий шаг требует отдельного запроса.
