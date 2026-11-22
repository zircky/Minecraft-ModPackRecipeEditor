# Phase 8 — Step 1, Step 2, Step 3

Дата: 18 ноября 2026. Реализован backend анализа в существующем `recipe-analyzer`. Step 4–6 и Phase 9 не начаты. Новый Analyzer Dashboard, ProductionGraphView, расчёты стоимости/энергии, impact/balance и экспорт пользовательских отчётов не входят в текущий запрос.

## Исходное состояние и ограничения Phase 1–7

Изучены RecipeIndex, RecipeDefinition, IngredientDefinition, RecipeOutput, RecipeChangeSet, RecipeEditorService/EffectiveRecipeIndex, RegistryIndex/TagIndex, wire RuntimeData.Snapshot и GT mapper/capabilities. В recipe-analyzer до этой работы был только package-info. Стартовый `./gradlew.bat test` прошёл.

GTCEu 7.1.4 имеет частичный runtime adapter и специализированный mapper Phase 7. GT 8.0 SNAPSHOT не подтверждён. Create/Mekanism integrations отсутствуют: эта фаза их не реализует и не имитирует. Неизвестные данные остаются непрозрачными, с предупреждениями и сохранёнными определениями.

## Архитектура и файлы

```text
recipe-analyzer/
  api/       AnalysisContext / Environment / Settings / Result / Diagnostic
             RecipeAnalyzer / AnalyzerRegistry / DefaultAnalyzers
  model/     effective input, runtime bridge, typed recipe facts/ports/operands
             version-scoped dependency adapters, canonical JSON fingerprints
  conflict/  DuplicateRecipeDetector / RecipeConflictAnalyzer
             VanillaConflictStrategy / GregTechConflictStrategy / IngredientOverlap
  graph/     RecipeDependencyGraph / RecipeGraphBuilder / RecipeGraphIndex
             AnalysisTagResolver / ReverseDependencyIndex / RecipeCycleDetector
```

Только этот модуль получил production-код. Он зависит от существующих recipe-engine, registry, companion-protocol и integrations; JavaFX/Minecraft API/тяжёлых graph libraries нет. Companion не изменён. companion-client используется только тестами для чтения настоящего snapshot через существующий RuntimeSnapshotService. Перечень файлов — PHASE8_FILES.txt.

Контекст захватывает версию Minecraft, loader, mods, original index, registry, snapshot, ChangeSet, integration capabilities, settings, timestamp, cancellation и dependency adapters. Оригиналы доступны отдельно. EffectiveAnalysisInput использует существующий RecipeEditorService.restore/effective, включая quarantine изменённых bases. Отдельная копия бизнес-правил применения ChangeSet не создавалась.

Режимы заданы явно:

- STATIC_PROJECT: исходный статический индекс + активные проектные изменения.
- SNAPSHOT_PROJECT: совместимый snapshot предпочитается по ID для неизменённых записей, затем учитываются активные UPDATE/CREATE/DELETE проекта. Static-only кандидаты сохраняются как неполные данные. Изменённые исходные references quarantine-ятся прежним сервисом.

Несовпадение Minecraft/loader/mod versions отключает runtime preference. Проверяются полнота snapshot, возраст/невалидное время, выставляется предупреждение об offline/time-scoped evidence. Этот backend не подтверждает live generation. Runtime-варианты сохраняются отдельными Recipe nodes; непрозрачные записи сохраняют ID/type/source/available representation. Нормализованное domain JSON — внутренний bridge, не восстановленный оригинал и не новый контракт экспорта.

Новые DTO не имеют полей Optional: неизвестные quantity/chance обозначены nullable значениями и uncertainty metadata. Существующие Phase 7 модели переиспользуются, не переписываются.

## Step 1 — Framework

Независимые passes реализуют `RecipeAnalyzer`. Registry проверяет ID/конфликты регистрации и выбранные passes. Dataset и graph готовятся один раз на captured context, тяжёлая работа предназначена для background worker. Исходники и снимок не записываются и не изменяются.

Общая диагностика содержит code/severity/message, recipe/resource references, evidence и suggestedAction. Есть бюджеты, notice об усечении и cancellation. `AnalysisResult.complete` означает завершение данного прохода в его бюджетах, **не полноту модпака или доказанную доступность ресурсов**.

## Step 2 — Duplicates и conflicts

- EXACT_DUPLICATE: полное совпадение canonical source JSON: порядок object keys не важен, порядок arrays и все неизвестные поля сохранены. Это не обещание одинакового runtime/load-order поведения.
- SAME_ID_CONFLICT: разные исходные определения одного ID. Победитель загрузки не угадывается.
- Runtime same-ID variants выделены как `RUNTIME_VARIANTS_SAME_ID`: могут быть легитимными category/proxy вариантами GT, не объявляются подтверждённым игровым конфликтом. Одинаковые partial runtime representations не объявляются полными exact source duplicates.
- EQUIVALENT_RECIPE: потенциально одинаковая целочисленная пропорция простых fully parsed Vanilla shapeless IO. Партии/время/производственная оптимальность не считаются доказанными. GT chance/conditions/EU не игнорируются ради объявления полного эквивалента.
- SIMILAR_RECIPE: совпавшие объявленные IO при различающихся свойствах. Только одинаковый выход не даёт диагностику duplicate/conflict; считается отдельная метрика shared-output groups.

Conflict strategy для Vanilla 1.20.1 знает поддержанные matcher predicates shaped/shapeless/cooking/stonecutting. Shaped сохраняет размеры/пустые клетки, trim границ и mirror; unordered inputs проверяются полным bipartite matching слотов, а не наличием одного общего ингредиента. Tag/альтернативные кандидаты сопоставляются с ограничением бюджета. Отсутствующие, пустые, статические или усечённые tags не подтверждают overlap. Подтверждённый результат означает пересечение известных matcher predicates в анализируемых данных, а не ошибочность рецепта или установленного победителя.

GT стратегия всегда консервативная: одинаковый IO может зависеть от machine/config/category/conditions/circuit/chance/custom lookup; вывод потенциальный. Другие моды без правил выбора не получают подтверждённый конфликт. Поиск различающихся tag/alternative overlaps выполнен для известных Vanilla форм; для GT/unknown types это не универсальное попарное исследование всех machine predicates.

Группировки линейные по размеру представлений; полного O(N²) прохода всего модпака нет. Candidate search ограничен числом memberships/comparisons, выдаёт complete=false и предупреждение при достижении лимита. Порядок bounded поиска воспроизводим для того же captured input.

## Step 3 — Graph, SCC, reverse index

Идентичность NodeKey = type + domain + id. Поддержаны ITEM, FLUID, CHEMICAL, MATERIAL, RECIPE, MACHINE, TAG; CHOICE и UNKNOWN представляют OR-группы и непрозрачные данные. `minecraft:water` item и fluid различны; chemical domains gas/infuse-type/pigment/slurry также различимы через адаптер. Данные Chemical/Material автоматически не выводятся из namespace/имени: нужен явный RecipeDependencyAdapter. Это контракт backend, не заявление об извлечении Mekanism/GT materials.

Рёбра идут resource → recipe → outputs. Несколько входов привязаны к отдельным слотам recipe node (AND), CHOICE/TAG задают OR. Сохраняются quantities, consumed/catalyst/unknown modes, per-recipe/per-tick cadence, chance/maxChance/boost и representation hash. Катализатор не превращается в расходуемый ресурс. Chance не превращается в гарантированное количество. Processor-family node — тип обработки, а не придуманная установленная машина. Unknown operands scoped к конкретному recipe/slot, не объединяют несвязанные неизвестные ресурсы.

TagIndex Phase 3 переиспользован для static candidates; resolved runtime tags берутся из соответствующего snapshot. Есть missing/empty/static/runtime states. Тег не заменяется одним произвольным членом, отсутствие static tag не доказывает недоступность. Shared membership contributions индексируются один раз на tag, а не копируются на каждый рецепт.

RecipeGraphIndex хранит ref-counted forward/incoming adjacency и contributions. При изменении/удалении индексируются только изменённые fragments; отпечаток учитывает IO semantics/capabilities, а не только Raw JSON. Публикация атомарна, прежний immutable graph не меняется. Capture нового graph snapshot и сравнение набора ключей линейны; это не обещание O(1) создания полного результата после любого изменения. Дополнительный preparation budget ограничивает работу, в том числе повторные ссылки shared nodes.

ReverseDependencyIndex выполняет ограниченный по глубине/visited budget обход downstream dependents, включая tags/choices, и возвращает truncation. Это структурная зависимость, не готовый RecipeImpactAnalyzer Step 5.

Для SCC выбран **итеративный Kosaraju**: O(V+E), уже есть forward/reverse indexes, нет риска JVM stack overflow на глубокой цепочке. Классификации: INFORMATIONAL_CYCLE, POTENTIAL_DEPENDENCY_DEADLOCK, POTENTIAL_RESOURCE_LOOP. Замкнутый modeled SCC может требовать внешнего seed, но worldgen/loot/trading неизвестны; цикл не объявляется доказанным deadlock. Простое deterministic single-resource amplification — кандидат resource loop с оговоркой об энергии/условиях/внешних ограничениях. Catalyst/alternative/recycling cycles допустимы. ERROR для таких предположений не создаётся.

## Бюджеты и ограничения

По умолчанию: 100000 recipe representations, 500000 nodes, 1500000 edges/preparation work, 1024 members/tag, 5000 diagnostics, 50 related references/diagnostic, 100000 candidate memberships/comparisons, snapshot max age 30 min. JSON representation ограничено 2 MiB, используется существующий bounded reader. При достижении лимита результат помечается неполным; произвольные пропущенные данные не выдаются за исчерпывающий граф.

Анализ не вычисляет loot/worldgen/trades, условия исполнения Java callbacks, технологические ограничения машины или obtainable status. Статический индекс неполон по определению; snapshot — свидетельство конкретной среды/времени. Сложные custom ingredients, dynamic smithing/output/NBT semantics могут остаться opaque. GT 8.0, Create и Mekanism extraction не добавлялись. Нет стоимости, expected material quantities, оптимизатора, energy conversion, balance rules или UI отчётов: это последующие шаги.

## Проверки

- `./gradlew.bat clean build`: **BUILD SUCCESSFUL**, 52 s, 64 actionable tasks (63 executed, 1 up-to-date).
- `./gradlew.bat test`: **BUILD SUCCESSFUL**, 1 s, 47 tasks up-to-date после чистой сборки.
- XML JUnit всех модулей: **268 tests, 250 passed, 18 skipped, 0 failures, 0 errors**. В recipe-analyzer обычный запуск: 27 passed, 2 opt-in skipped.
- Проверен production dependency DAG: 12 модулей, циклов нет. App/desktop-ui исходники и build files не изменены.
- Логи: `.manual-tests/phase8/evidence/clean-build.log`, `test.log`, `final-test-counts.txt`.

Новые тесты: AnalysisFrameworkTest, DuplicateRecipeDetectorTest, RecipeConflictAnalyzerTest, RecipeDependencyGraphTest, RecipeCycleDetectorTest, RuntimeAnalysisTest; opt-in AnalyzerSnapshotIntegrationTest и AnalyzerStressTest. Проверены overlay/quarantine, same ID/exact/proportional/same-output cases, все требуемые typed domains, теги/alternatives/catalyst/chance, новые/удалённые/изменённые fragments, смена adapter semantics, cancellation, partial budgets, runtime scope/malformed DTO и глубокий SCC обход.

Дополнительный прогон всех analyzer тестов с обоими opt-in сценариями: **29 passed, 0 skipped, 0 failures/errors**. Источники реальных интеграционных данных — snapshot от выполненной ранее Minecraft/Forge/GT/KubeJS проверки Phase 7. **Minecraft в Phase 8 не запускался**; это проверка анализа реальных сохранённых данных, не новая live integration.

Реальный snapshot: `72f7d4b1-0fbd-45f9-a907-651491aeb02e`, Minecraft 1.20.1 Forge 47.4.10, GTCEu 7.1.4, KubeJS 2001.6.4-build.120. 35194 main recipe records → 36081 variants. Graph: **58345 nodes, 157097 edges**, без graph truncation, с unknown-dependency evidence. SCC: 211 cycles, в том числе 10 potential modeled deadlock candidates; это не подтверждённые игровые проблемы. 887 same-ID runtime representation groups — варианты, не список 887 неисправных рецептов. Conflict search достиг 100000 comparisons и корректно complete=false; найденные 68 Vanilla matcher overlaps не означают 68 ошибочных recipes.

В последнем прогоне snapshot pipeline занял 24433 ms. Synthetic fixture: 60000 recipes, 600 **искусственных mod identities**, 120001 nodes, 120000 edges, без truncation, 4908 ms; used heap at end 598736896 bytes (это не peak). Это наблюдения текущего окружения, не гарантия для любого модпака. Линейный deep-chain unit test содержит 6000 recipes. Синтетические identities не выдаются за запущенный настоящий 600-mod modpack.

Свидетельства сохранены вне clean outputs: `.manual-tests/phase8/evidence/phase8-snapshot-result.txt`, `phase8-stress-result.txt`, `test-summary.txt`. Реальные отдельные Vanilla/Create/Mekanism modpack-прогоны Phase 8 не выполнялись; Vanilla покрыт unit-тестами и частью реального GT snapshot. Старые тесты Phase 1–7 сохраняются. Для новых файлов использован standalone [Google Java Format 1.25.2](https://github.com/google/google-java-format/releases/tag/v1.25.2), без зависимости formatter в приложении.

## Использование backend

Работайте с captured immutable inputs на background worker, не вызывайте analyze на JavaFX thread:

```java
var context = AnalysisContext.staticProject(
        "1.20.1", "FORGE", detectedModVersions,
        editor.originals(), registryIndex, editor.snapshot());
var analyzers = new DefaultAnalyzers().registry();
var results = analyzers.analyze(context, Set.copyOf(analyzers.ids()));
RecipeDependencyGraph graph = context.dependencyGraph();
var affected = new ReverseDependencyIndex().dependents(
        graph, new NodeKey(DependencyNodeType.ITEM, "minecraft-item", "namespace:item"),
        16, 10000, () -> Thread.currentThread().isInterrupted());
```

В этом примере namespace:item — placeholder API параметра, не добавляемый рецепт/ресурс. В context доступны оригиналы; quantities/unknown/slot metadata читаются из dataset и edges. Для следующего incremental capture передайте тот же RecipeGraphIndex в новый AnalysisContext с новым ChangeSet. Для snapshot scope используйте полный constructor с AnalysisEnvironment.Scope.SNAPSHOT_PROJECT, реальным RuntimeData.Snapshot, его metadata/capabilities. Этот режим offline; pipeline сам не подключается к Minecraft.

Команды:

```powershell
.\gradlew.bat :recipe-analyzer:test
.\gradlew.bat :recipe-analyzer:test --tests '*AnalyzerSnapshotIntegrationTest' '-PanalyzerSnapshot=L:\My Add\RecipeEditor\.manual-tests\phase7\evidence\modified.mresnapshot'
.\gradlew.bat :recipe-analyzer:test --tests '*AnalyzerStressTest' -PanalyzerStress=true
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat :app:run
```

UI остаётся текущим приложением Phase 7. Пункт Analyzer ещё не получил Dashboard/Production Tree: это Step 6. Команда `./gradlew.bat :app:run` запущена: после создания главного окна записано `Started Minecraft Recipe Editor 0.1.0-SNAPSHOT` (02:15:29). Приложение оставлено работающим. В логе присутствует прежнее JavaFX предупреждение об unnamed module; запуска оно не остановило. Проверен старт по логу, визуальная проверка GUI и его сценариев в Phase 8 не выполнялась. Лог: `.manual-tests/phase8/evidence/app-run.log`.

Phase 8 остановлена после Step 3 согласно последней инструкции задания. Полная Phase 8/Phase 9 не объявляется завершённой.
