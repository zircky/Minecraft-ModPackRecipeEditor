# Phase 9 — Step 1: Technical Audit

Дата: 18 ноября 2026. Аудит выполнен до изменений тестовой инфраструктуры. Текущий запрос ограничен Step 1–2. Этот документ фиксирует исходное состояние; результаты новых проверок находятся в PHASE9_REPORT.md.

## Проверенные области

Изучены настройки всех 12 desktop/shared модулей и отдельного Forge build; состав production/test sources; отчёты Phase 2–8 и сохранённые результаты; критические пути import/discovery, editor/ChangeSet/history, project persistence, export planner/writer/manifest, protocol/client/server, Forge thread bridge, UI lifecycle/search/previews. Инвентаризация файлов сохранена в `.manual-tests/phase9/evidence/source-inventory.txt`. Статический обзор не заменяет профилирование или запуск каждого GUI-сценария.

Исходный `gradlew.bat test`: BUILD SUCCESSFUL, 47 задач up-to-date. Последний XML baseline: 268 tests, 250 passed, 18 skipped, 0 failures/errors. Это исходная проверка, не новое выполнение ранее пропущенных live/stress сценариев.

## Архитектура и Gradle

- 12 модулей: app, core, modpack-parser, registry, recipe-engine, recipe-analyzer, integrations, exporters, companion-protocol, companion-client, companion-server, desktop-ui.
- Desktop Java 21. Protocol/server компилируются с release/toolchain 17. Forge 1.20.1 / 47.4.10 собирается отдельно Gradle 8.8 / ForgeGradle 6; root wrapper Gradle 9.2.0 не оценивает ForgeGradle. Компиляция Forge проверялась ранее; в текущем аудите новая Forge сборка не заявляется.
- UI зависит от сервисов, app компонует их. Core не зависит от JavaFX; engine не зависит от Minecraft API. Production project dependency graph не содержит циклов; добавляется автоматическая проверка фактических Gradle dependencies и границ bytecode.
- Нет потребности в новом integration-tests/test-fixtures модуле или ArchUnit. Для текущих проверок достаточны JUnit 5, встроенные Gradle Test/TestReport и java-test-fixtures в app.
- MainWindow совмещает navigation, lifecycle, project/import/runtime/export coordination; усложнение концентрируется здесь. Полное переписывание в рамках аудита не оправдано.

## Реализованное и незавершённое

Phase 1–6: импорт, статические индексы, редактор с overlay/undo/redo, сохранение, три export formats, read-only Companion и snapshot persistence. Статическая индексация по определению неполна. Экспорт хранит `GENERATED_RUNTIME_UNVERIFIED`; SUCCESS означает запись файлов, не применение в игре.

Phase 7 выполнена только Step 1–2: проверенный частичный профиль GTCEu 7.1.4; установленная ранее 8.0.0-SNAPSHOT+e24b238 не имеет подтверждённого адаптера. Create/Mekanism адаптеры отсутствуют. Phase 8 выполнена только Step 1–3: framework, duplicates/conflicts, graph/SCC. Material/energy cost, impact/balance, production tree/dashboard/report export отсутствуют. Нельзя тестами объявить эти функции реализованными.

## Реестр проблем

| ID | Priority | Evidence / последствие | Следующее действие |
|---|---|---|---|
| A01 | HIGH | Нет crash-session/autosave recovery. `.bak` в FileProjectPersistence — резервная копия сохранённого проекта, не восстановление несохранённых правок. | Блокирует release gate; реализация Step 4–5 по отдельной команде. |
| A02 | HIGH | Отсутствуют ранее отложенные функции Phase 7/8; GT8 SNAPSHOT unsupported. | Не объявлять Release 1.0 готовым, согласовать продуктовый scope; не подменять заглушками. |
| A03 | HIGH | FileProjectPersistence/EditorTasks/CompanionConnectionService используют single-thread executor и shutdownNow без завершения futures извлечённых из очереди. По контракту executor queued CompletableFuture tasks могут остаться незавершёнными. ExportService использует shutdown и допускает продолжение очереди. | Риск lifecycle/cancellation, не подтверждённая потеря данных; проверить управляемым воспроизведением и исправить в Step 4. |
| A04 | MEDIUM | RecipeBrowserView FilteredList и ResourceBrowserView RegistrySearchService выполняют линейную фильтрацию в UI callback; runtime rows setAll/merge application также требуют проверки. | P95/FX pulse измерения Step 3. Сейчас нет доказанного нарушения 100 ms. |
| A05 | MEDIUM | TextureResourceService ограничен 64 entries и 128 queued tasks; cache key = ResourceSource, без content version/root. При изменении того же JAR возможен stale preview. close очищает cache, отдельного project invalidation нет. | Проверить повторное открытие/изменение сборки; не утверждать memory leak без retained-object анализа. |
| A06 | MEDIUM | Import/index/editor/storage/client executor queues не ограничены. UI частично ограничивает busy операции, но API не задаёт backpressure. | Измерить нагрузки/очереди; вне Step 2 не вводить произвольные пределы. |
| A07 | MEDIUM | Logback имеет daily rotation/maxHistory=14, но нет size cap/totalSizeCap. INFO пишет абсолютные пути. Diagnostic Dashboard и operation IDs отсутствуют. | Logging/diagnostics improvements следующими шагами. Токены не обнаружены в явных logger calls; это не полная гарантия отсутствия любых секретов в исключениях. |
| A08 | MEDIUM | Есть unit и несколько межмодульных сценариев, но нет отдельной integration task/сводного отчёта и воспроизводимой общей lifecycle fixture; stress находится в test source set с opt-in property. | Исправляется Step 2: suites, fixtures, lifecycle/regression/architecture checks. |
| A09 | MEDIUM | ExportFileWriter содержит staging, hashes, journal, rollback; abrupt-process kill не равен обработанному IOException. Persistence имеет backup, force, atomic move с fallback; сбои диска/ACL/power-loss не испытаны. | Добавить детерминированные regression tests для доступных failure seams; реальные fault campaigns Step 4–5. |
| A10 | LOW | Classpath JavaFX выдаёт unnamed-module warning; существующий код местами плотный, wildcard imports. | Не блокирует старт; не менять работающую конфигурацию без необходимости. |

CRITICAL: подтверждённых дефектов этого уровня в проведённом обзоре нет. Это не утверждение отсутствия неизвестных ошибок. HIGH A01/A02 — подтверждённые release gaps, A03 — обоснованный риск по коду, требующий отдельного воспроизведения. Массовые stability изменения не входят в текущие Step 1–2.

## Данные, потоки, безопасность

Project persistence: version/checksum, ограничения 48 MiB / 10000 changes, prevalidation, временный файл + force, `.bak`, отказ при повреждённом существующем проекте, запрет сохранения внутрь modpack/symlink. Changed source references quarantine-ятся RecipeEditorService. Требуется новая проверка повторного жизненного цикла с реальным повторным import/index и сохранением unknown fields.

Export: preflight и повторная проверка preview/hashes, managed relative paths, symlink checks, target lock, stage + rollback, recovery journal при неудачном rollback. Имеются тесты injection через ApplyHook. Добавляется сквозной сценарий устаревшего preview/пользовательского изменения и source hash checks. Генерация не объявляется runtime verification.

Companion: bind 127.0.0.1, mutual HMAC challenge, ограниченные frames/records/transfers, session identity/generation checks, cancel закрывает socket. ForgeSnapshotProvider планирует capture через MinecraftServer.execute; игровые jobs обрабатываются tick с лимитом 128 records / 4 ms. Методы start/stop/invalidate зависят от Forge event lifecycle; без новой Minecraft проверки нельзя подтвердить все interleavings/reload сценарии. Server сохраняет только одну активную сессию и bounded send queue. Пустые catch при best-effort socket close не равно подавлению ошибок основных операций.

ResourceScanner/ModScanner/SafeFiles ограничивают archive/metadata reads, проверяют containment и paths. Texture loader закрывает ZIP/ImageIO readers, проверяет размер PNG/геометрию. Static analysis не исполняет scripts/classes. Это обзор safeguards, не полный security pentest.

RecipeIndex/RegistryIndex и captured results разделены с UI; editor синхронизирует mutation и snapshots. UI callbacks обычно используют Platform.runLater, browser снимает editor listener при close; connection listener RuntimeIntegrationView не удаляется, closed flag лишь предотвращает UI update — возможное удержание view до закрытия connection, без profiler не подтверждённая утечка.

## Не выполненные проверки

В Step 1 не запускались Minecraft/GT8/Create/Mekanism, JFR/VisualVM/JMH, heap-retention/GC анализ, FX pulse/P95, 100000 recipes/200000 resources/500 real mods, disk-full/ACL/power-loss, Windows 10/11 matrix, IntelliJ launch. Результаты старых фаз — исторические свидетельства с их ограничениями, не новая runtime verification.

Release status: **NOT READY**. Причины: незавершённые возможности, recovery и lifecycle risks, непроведённые profiling/security/runtime/platform gates. Step 2 устраняет пробел тестовой инфраструктуры, но не закрывает все release gates.
