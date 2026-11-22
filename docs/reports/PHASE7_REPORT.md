# Phase 7 — Step 1 и Step 2

Реализованы Integration Framework и профиль GregTech Modern 7.1.4. Create, Mekanism, полноценный Integration Manager (Step 5) и Phase 8 не начаты. Поддержка версий/свойств ограничена подтверждённой матрицей ниже; Phase 7 целиком не объявляется завершённой.

## Анализ Phase 1–6

Перед изменениями существующий `./gradlew.bat test` завершился BUILD SUCCESSFUL. Изучены RecipeDefinition, RecipeParser, IngredientDefinition, RuntimeRecipeAdapter, RecipeEditorService, ProjectDocument/FileProjectPersistence, RecipeExporter/ExportPlanner, Static + Runtime Merge и независимый Forge build.

Сохранены Java 21/JavaFX/Gradle Kotlin DSL/Jackson/SLF4J/Logback/JUnit 5 на Desktop и Java 17/ForgeGradle/Gradle 8.8 на Companion. Отдельная система сохранения изменений не создавалась; проект остаётся version 1 и хранит JSON/ChangeSet прежнего формата.

## Архитектура

`integrations.api`: ModIntegration, IntegrationContext, IntegrationCapabilities, IntegrationRegistry, SupportLevel, ModVersion. Registry явно регистрирует владельцев mod IDs, отвергает конфликт адаптеров и поддерживает отключение на уровне API для текущей сессии. Контракты mapping, runtime mapping, validation, editing и export разделены. Существующий Minecraft-dependent RuntimeRecipeAdapter остаётся в Forge Companion; Minecraft/JavaFX API в recipe-engine отсутствуют.

`integrations.gregtech`: typed GregTechRecipeDefinition, GregTechContent, GregTechCondition; mapper, parser, validator, tree-patch editing service, checked KubeJS generator и explicit runtime-draft copier.

Производственные зависимости: `integrations -> recipe-engine, core, companion-protocol`; `exporters -> integrations, recipe-engine, core`; `companion-client -> recipe-engine, companion-protocol`; `desktop-ui -> integrations, companion-client, exporters`; `app` связывает сервисы. Новая зависимость companion-client на exporters существует только для opt-in тестов. Производственных циклов нет.

RecipeDefinition не дублируется: специализированная модель маппится из существующего JSON/DTO, parser добавлен в ProjectRecipeParsers. В IngredientDefinition добавлены Sized, Fluid и Custom с полным исходным представлением; item/tag/alternatives совместимы с прежними конструкторами. RecipeOutput.Capability сохраняет mod-specific output без объявления вероятностного результата гарантированным ItemStack. Индексы обходят Sized и item capability outputs; fluid IDs не попадают в item lookup.

Runtime DTO из Phase 6 используется напрямую: нет передачи игровых объектов и новой сетевой команды записи. Сбор GT по RecipeManager/category/proxy collections и нормализованный snapshot остаются из Phase 6. Mapping/editor выполняются на Desktop. Не увеличивался объём сериализации на игровом потоке; сохраняется ограничение Phase 6: одна большая запись и начальное перечисление не прерываются tick-budget.

## Версии и совместимость

| Компонент | Статус |
|---|---|
| Minecraft 1.20.1 + Forge 47.4.10 | Реальные интеграционные проверки |
| GTCEu Modern 7.1.4 | Проверенный профиль чтения/модели/редактирования; ограниченный KubeJS export |
| KubeJS 2001.6.4-build.120 | Проверен GT schema factory + codec merge на integrated server |
| Rhino 2001.2.3-build.6, Architectury 9.2.14 | Использованы в реальном тестовом окружении |
| GTCEu 8.0.0-SNAPSHOT+e24b238 | Обнаружен и изучен установленный JAR, совместимость UNKNOWN; специализированные edit/export отключены |
| Прочие GT/KubeJS версии | Не считаются совместимыми автоматически |

ModVersion разделяет numeric version, prerelease и build identity; `7.1.40`, `7.1.4+unverified`, release и SNAPSHOT не взаимозаменяются. В IntegrationContext предусмотрены artifact hashes; совместимость SNAPSHOT не подтверждается только по префиксу версии. Найденный файл `gtceu-1.20.1-8.0.0-SNAPSHOT+20261104-224207-e24b238.jar` имеет mod version `8.0.0-SNAPSHOT+e24b238`; hash/ABI inspection сохранены в `.manual-tests/phase7/gt8-inspection.txt`.

Изучены [ветка 1.20.1](https://github.com/GregTechCEu/GregTech-Modern/tree/1.20.1), [документация](https://github.com/GregTechCEu/GregTech-Modern/tree/1.20.1/docs), [GTRecipe](https://raw.githubusercontent.com/GregTechCEu/GregTech-Modern/1.20.1/src/main/java/com/gregtechceu/gtceu/api/recipe/GTRecipe.java) и [GTRecipeBuilder](https://raw.githubusercontent.com/GregTechCEu/GregTech-Modern/1.20.1/src/main/java/com/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder.java). Они не использованы как доказательство ABI 7.1.4: её публичные классы/codec/schema проверены отдельно javap по установленному JAR.

В установленном 8.0 Content — record с content/chance/maxChance, без tierChanceBoost, в отличие от публичных полей 7.1.4. Это согласуется с текущим [Content API](https://raw.githubusercontent.com/GregTechCEu/GregTech-Modern/1.20.1/src/main/java/com/gregtechceu/gtceu/api/recipe/content/Content.java). Старый reflection adapter не расширен слепо на 8.0. Неподтверждённая сборка получает диагностику; generic metadata/исходный Raw JSON остаются доступны. Реальный Minecraft с 8.0 в Phase 7 не запускался.

## Данные и редактирование

Модель сохраняет ID/type/category, роли item/fluid/tick contents, optional quantities/EU/duration, chance/maxChance/boost, non-consumable semantics, typed condition discriminator и полные неизвестные свойства. Tag/circuit/custom/NBT representation не превращаются в ItemStack. Отсутствующее поле остаётся отсутствующим. Числа chance не нормализуются молча к универсальной шкале.

GregTechRecipeEditorProvider реализует JavaFX-only RecipeEditorProvider/RecipeEditorContext. Форма показывает только существующие leaf fields: Duration, quantities, item/fluid IDs и tags, tick EU, chance/maxChance/boost. Остальные данные сохраняются и доступны в Advanced/Raw JSON. Apply патчит только выбранный узел; несохранённые/неприменённые поля блокируют Save и не теряются при ошибке patch. Общие Raw JSON/Diff/Save/Undo/Redo остаются из Phase 4. При Save повторно выполняется RecipeValidator с GregTechRecipeParser/Validator.

Проверяются тип/структура, целые положительные Duration/количества, EU, диапазоны относительно объявленного maxChance и наличие condition type. Machine/config-specific IO limits и доступность mod-specific condition codecs без достоверных данных обозначаются предупреждениями. Нет универсальных лимитов слотов машин. Неизвестные GT schemas сохраняют Raw JSON, не получают ложную семантическую поддержку.

В Recipes после **совместимого** runtime merge есть явное **Copy supported GT state to NEW project recipe**. Копия создаёт новое определение проекта; исходный snapshot не меняется и не называется восстановленным оригиналом. Неоднозначные runtimeVariants отклоняются. Runtime IDs учитываются при CREATE и в сервисе, предотвращая коллизию. Редактирование/удаление runtime-only оригинала без статического source reference не добавлено: для него нужен отдельный provenance/persistence контракт. UPDATE/DELETE статических оригиналов используют прежнюю проверку source references/hashes.

Toolbar **GregTech capabilities** показывает фактическую версию, возможности и предупреждения импортированной среды. Это небольшой диагностический диалог Step 1–2; полный Integration Manager/его UI toggle относится к последующему Step 5.

## Экспорт

Phase 5 KubeJsRecipeExporter и Gtceu714Adapter переиспользованы; adapter делегирует typed mapper/validator/generator, а не содержит второй набор GT-преобразований. ExportPlanner/preview/manifest/file writer/сохранённые изменения остаются прежними. Unsupported property блокирует весь набор; отсутствует тихий частичный экспорт.

Профиль генерации — assembler, macerator, chemical_reactor для точных GTCEu/KubeJS версий. Генератор использует `event.recipes.gtceu.<machine>(builderId).merge(codecJson)`. UPDATE выполняет exact-ID remove, затем добавление; DELETE — exact-ID remove. У GT schema factory есть machine prefix и особая обработка minecraft namespace, поэтому ID проверяется заранее: например `recipe_editor:assembler/name`, namespace не minecraft. Несовместимый ID отклоняется вместо скрытого переименования.

Scalar EnergyStack codec преобразуется только для KubeJS reader в эквивалентный объект voltage/amperage=1: это проверено по публичному EnergyStack(long) constructor 7.1.4 и фактическому загруженному EU/t. Исходный RecipeDefinition.rawJson при этом не меняется. Chance/non-consumable/fluids не отбрасываются. Unknown nested fields, неподтверждённые condition/research/NBT/custom ingredient codecs и дополнительные capabilities блокируют export. Datapack/CraftTweaker GT export не заявлен.

## Матрица фактически проверенных возможностей

| Mod | Recipe Type | Read | Edit | Export | Runtime Tested |
|---|---|---|---|---|---|
| GTCEu 7.1.4 | assembler, проверенный subset | DTO + typed model | Leaf form/Raw/Diff + existing ChangeSet | KubeJS CREATE/UPDATE/DELETE, item/fluid, EU/duration, chance/boost/non-consumable | Да: baseline + после restart, новые DTO и full JSON comparison |
| GTCEu 7.1.4 | macerator / chemical_reactor, checked codec subset | Typed JSON/DTO | Общая GT leaf form и Raw | Генерация проверена unit tests/API; отдельный end-to-end для этих машин не выполнен | Не заявлено |
| GTCEu 7.1.4 | conditions/research/custom properties | Полностью сохраняются доступные JSON fragments | Raw; поддержанные leaf fields | Непроверенные codecs блокируют экспорт | Generic extraction Phase 6; полный roundtrip не заявлен |
| GTCEu 8.0.0-SNAPSHOT+e24b238 | machine recipes | Generic metadata / доступный static Raw | Specialized UNSUPPORTED, Raw сохранён | UNSUPPORTED | Не выполнен |
| Create | — | Не реализовано в Step 1–2 | Не реализовано | Не реализовано | Не выполнен |
| Mekanism | — | Не реализовано в Step 1–2 | Не реализовано | Не реализовано | Не выполнен |

Read/edit поддержка не объявляется доказательством export/runtime roundtrip. Профиль в целом PARTIALLY_SUPPORTED; каждый конкретный экспорт проходит строгую проверку.

## Проверки

| Проверка | Результат |
|---|---|
| Финальный `./gradlew.bat clean build` | BUILD SUCCESSFUL, 49 s |
| Отдельный `./gradlew.bat test` | BUILD SUCCESSFUL, 2 s; UP-TO-DATE после clean build |
| JUnit XML | 239 total: **223 passed, 16 skipped, 0 failures/errors** |
| `./gradlew.bat :app:run` | Продуктовый JavaFX запущен, startup подтверждён логом |
| `./gradlew.bat buildCompanion` | BUILD SUCCESSFUL, 1m 9s; child Java 17 build 33 s |
| Production graph | 12 modules, acyclic; dependency-graph.json |

16 skips обычного прогона: 10 opt-in Minecraft/runtime/adapter tests, 4 ранее существующих real-pack tests и 2 platform-dependent symlink tests. Пропуски не считаются Minecraft success. Phase 7 live test отдельно выполнялся успешно: baseline 1m 25s, modified после нового Minecraft startup 3m 9s. Старые real-pack и Phase 6 live tests в этом финальном обычном прогоне не запускались.

Новые unit tests: IntegrationRegistryTest, IntegrationCompatibilityTest, GregTechRecipeMapperTest, IntegrationExportTest; дополнены Gtceu714AdapterTest и ProjectPersistenceTest. Проверены отсутствие/несовместимость/отключение/конфликт адаптеров, snapshot build identity, quantities/tags/fluids/EU/chance/non-consumable/conditions/unknown preservation, patch, source/runtime-ID conflict, Undo/Redo, project v1 roundtrip и экспорт всех трёх операций. Unit tests не требуют Minecraft.

Реальные проверки opt-in GregTechKubeJsLiveTest: используется сохранённый **настоящий** GT snapshot для подготовки моделей, общий RecipeEditorService для изменений, тот же generator, затем клиент Minecraft 1.20.1 Forge 47.4.10 + GTCEu 7.1.4 + KubeJS 2001.6.4-build.120 на Java 17. После применения сценария новая Desktop-сессия читает реальные DTO через Companion и сравнивает содержимое. Для UPDATE/DELETE выбраны предсуществовавшие GT IDs, а не добавления того же события. Ожидаемые fixture JSON явно являются копиями runtime-состояния, не «оригинальным исходником».

Baseline подтвердил исходные `gtceu:assembler/cover_gold_wire_gt_double_styrene_butadiene` и `gtceu:assembler/cover_silver_wire_gt_quadruple_styrene_butadiene`. Modified проверяет replacement первого, отсутствие второго и создание `mre_phase7_test:assembler/created`, точные Duration=137, EU/t=11, non-consumable chance=0, output chance=8000/maxChance=10000/boost=200 и item/fluid contents. Сначала также прошёл сценарий с двумя новыми test copies. Обе проверки прошли. Baseline и modified snapshots содержат по 35194 recipes: удаление одного исходного ID и добавление нового сохраняет общее число. Фактический Minecraft log modified: added 2 / removed 2 / failed 0. Полный normalized codec JSON двух resulting recipes семантически равен expected JSON, включая type/category, по отдельной проверке `full-json-comparison.txt`. Это подтверждение зарегистрированного состояния рецептов, а не тест производства продукции в построенной машине.

Свидетельства сохраняются вне build: `.manual-tests/phase7/evidence/` — baseline/modified script, expected JSON, IDs, result.txt и реальные `.mresnapshot`. Эти файлы переживают clean. Новые test recipes существуют только в отдельном workspace dev world/скрипте. Исходные инстансы и пользовательские миры не изменены.

JavaFX `:app:previewGregTech` реально запускался с настоящим GT snapshot: наблюдались специализированная форма, item/fluid quantities, EU, chances и общие вкладки. Ручной click-through Save не заявляется; изменение/валидация/история/сохранение проверены сервисными тестами. Preview — только test classpath, не входит в production distribution.

Dedicated dev-server с этим KubeJS не прошёл загрузку: BlockColor client-class load на SERVER. Успешная runtime-матрица относится к integrated server. Ранние неверные способы установки GT ID и scalar EU были отвергнуты реальными проверками; production generator использует подтверждённые schema factory и EnergyStack encoding.

## Запуск и воспроизведение

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat :app:run
.\gradlew.bat buildCompanion
```

Для UI импортируйте Minecraft 1.20.1 Forge pack с GTCEu 7.1.4, проверьте GregTech capabilities. Статический/проектный GT рецепт откройте в Recipes → Edit. Для runtime copy установите Companion, подключитесь, синхронизируйте ту же импортированную среду; Source Runtime → Copy → новый ID с machine prefix. Apply/Validate → Save to project → Save Project → Export All Changes → KUBEJS → Preview → Export. Готовые файлы устанавливайте в отдельное тестовое окружение и проверяйте свежим snapshot, а не только успешным Export.

Для opt-in проверки нужны JDK 17/21, отдельный Forge dev-world и локальные copies GT/KubeJS/Rhino/Architectury/embedded dependencies, не включённые в JAR. `:companion-client:test --tests '*GregTechKubeJsLiveTest'` принимает `phase7Mode=prepare|baseline|modified`, `phase7Directory`, `runtimeSnapshotFile` для prepare; для live — runtimeSessionFile/runtimeReadyFlag/runtimeProbeMarker/runtimeProbeRunId. Child wrapper `runClient -PwithGameTests -PwithKubeJS -PgtReferenceDirectory=<copies> -PquickPlayWorld=<test-world> -PprobeRunId=<same-unique-id>`. Перед каждым запуском новый nonce/run ID; скрипт stage копируется только в isolated `run-client/kubejs/server_scripts`. После successful probe Minecraft закрывается test-only hook. Production Companion такого управления не содержит.

## Ограничения

- Это Step 1–2 с версионным profile, а не завершение всех Advanced Mod Integrations.
- GT 8.0 SNAPSHOT e24b238 не получает подтверждённых edit/export/runtime capabilities. Для неё нужен отдельный version-specific adapter и реальный roundtrip.
- Не все GT машины/ingredients/conditions/research/callbacks экспортируются. Unsupported поля не теряются: экспорт блокируется.
- Runtime-only baseline не превращается автоматически в статический original: доступна явная новая копия, но не source-less UPDATE/DELETE в UI.
- Нет нового Material Browser/редактора материалов, Create/Mekanism/Chemical Browser или полноценного Integration Manager. Эти функции не включены в текущую матрицу Step 1–2.
- Число/порядок параллельных сторонних KubeJS scripts может влиять на результат; успешный export не считается runtime verification. Это проверяется отдельно snapshot после загрузки.
- Ограничения и provenance snapshots из Phase 6 сохраняются. Не выполнен полный gameplay-тест Cosmic Frontiers или ISG.

Перечень новых/изменённых файлов — PHASE7_FILES.txt. Производственный JAR: `minecraft-companions/forge-1.20.1/build/libs/mre_companion-forge-1.20.1-0.1.0.jar`, 78211 bytes, 39 classes, все major 61 (Java 17). GameTests/ClientIntegrationHooks/JavaFX/GT/KubeJS libraries отсутствуют. SHA-256: `82d102626670f5c5be7dde26e777893c18061c3b61c1b2975f039676f8db1d2e`.

SHA-256 исходного GTCEu 7.1.4 JAR не изменился: `8450217d1aaae9645a6ea645897222cee27f13e9f163ec113a2a1d9f0c390f37`. Baseline snapshot SHA-256: `400d8cdefd32f3340db9ecf540b60e22612bc30e47493996504b8afab7ddea04`; modified snapshot SHA-256: `14101ade17997a37dc9cdc437e89eb22a9130ebd7b180cb693f793bdf6f70ba0`.

Предупреждения существующей classpath JavaFX конфигурации и deprecated FMLJavaModLoadingContext.get() не блокируют сборку/запуск. Продуктовое окно после контрольного запуска оставлено открытым.
