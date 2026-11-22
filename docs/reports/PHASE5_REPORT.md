# Phase 5 — Recipe Export System

Реализованы генерация Datapack / KubeJS / CraftTweaker, проверка совместимости, планирование, безопасная запись, профили, manifest и JavaFX Export Wizard. Phase 6 не начата. Поддержка ограничена описанными ниже адаптерами; успешная генерация файлов не означает успешную загрузку в Minecraft.

## Архитектура

Сохранены десять существующих модулей. Новых Gradle-модулей и циклов нет:

```text
app: composition, ProjectPersistence, lifecycle
  ├─ desktop-ui → exporters, recipe-engine, core
  ├─ exporters → recipe-engine → registry/core
  ├─ integrations → recipe-engine (Vanilla 1.20.1 parsers)
  ├─ modpack-parser → core
  └─ остальные существующие сервисы анализа
exporters tests → integrations → recipe-engine
```

Прямые зависимости exporters на core и recipe-engine сохранены; recipe-engine экспортируется как API, поскольку типы движка входят в контракт. SLF4J добавлен в exporters, существующая конфигурация Logback приложения используется без дублирования. Java 21, Jackson, JUnit 5, JavaFX и Gradle Kotlin DSL сохранены.

`RecipeExporter` отвечает за capability и генерацию своего формата. Форматы не объединены в универсальный switch. `ExportContext` содержит импортированную сборку (включая версии Minecraft/Forge/модов), неизменённый индекс, сохранённый ChangeSet, настройки профиля, предыдущие manifest и разрешение записи под source root. `ExportRequest` задаёт точное множество ID, индивидуальные overrides формата и отдельный список явно выбранных неизменённых копий.

`ExportValidator` использует существующий RecipeValidator. Он проверяет ID/JSON/shape, текущие исходные references и SHA-256 JSON непосредственно в файле/JAR. Изменившиеся, исчезнувшие и карантинные источники блокируют экспорт. Для CREATE проверяется коллизия с оригиналом. Неизменённая копия — отдельный явный режим, а не команда изменения проекта.

`ExportPlanner` выполняет только bounded reads. Он формирует immutable `ExportPlan`: все выбранные операции, capabilities и альтернативы, выходные пути и точные тексты, ожидаемые хеши старых файлов, устаревшие пути и типизированную диагностику. AUTO предпочитает Datapack для совместимого CREATE/UPDATE, затем KubeJS, затем CraftTweaker. Выбор определяется типом и API, а не namespace. Namespace проверяется дополнительно только там, где сам API накладывает ограничение. Неподдерживаемые операции сохраняются в плане и блокируют применение целиком.

`RecipeExportService` использует один последовательный virtual-thread worker, без потока на каждый рецепт. Dry Run не пишет файлы. Перед применением preview строится повторно; изменения файлов/хешей/плана блокируют запись устаревшего preview. `ExportControl` допускает отмену до применения и атомарно закрывает эту возможность на время применения. Файлы и manifest сохраняются через `ExportFileWriter` и `ExportMetadataStore`.

UI повторно загружает project.json с диска, материализует сохранённые изменения в фоне и передаёт их мастеру. Несохранённые формы и новый ChangeSet в памяти не попадают в экспорт. Старый формат project.json v1 не менялся. Экспортные данные находятся рядом: `<project>.exports.json`, версия 1 с SHA-256 payload. Сохраняются профили и последний manifest для пары profile/target. Исторические manifest других каталогов не теряются при изменении target. Это контроль целостности, не цифровая подпись.

## Совместимость

Матрица означает реализованную и проверенную тестами **генерацию** при полном поддержанном JSON; runtime-совместимость не подтверждалась.

| Recipe Type | Datapack | KubeJS | CraftTweaker |
|---|---|---|---|
| Crafting Shaped | CREATE / UPDATE | CREATE / UPDATE / DELETE | CREATE* / UPDATE* / DELETE |
| Crafting Shapeless | CREATE / UPDATE | CREATE / UPDATE / DELETE | CREATE* / UPDATE* / DELETE |
| Smelting | CREATE / UPDATE | CREATE / UPDATE / DELETE | CREATE* / UPDATE* / DELETE |
| Blasting / Smoking / Campfire Cooking | CREATE / UPDATE | CREATE / UPDATE / DELETE | CREATE* / UPDATE* / DELETE |
| Stonecutting | CREATE / UPDATE | CREATE / UPDATE / DELETE | CREATE* / UPDATE* / DELETE |
| Smithing Transform / Trim | CREATE / UPDATE | CREATE / UPDATE / DELETE | CREATE* / UPDATE* / DELETE |
| GTCEu assembler / macerator / chemical_reactor | UNSUPPORTED | Ограниченный статический адаптер 7.1.4 | UNSUPPORTED |
| Неизвестные/неполные/частично разобранные рецепты | UNSUPPORTED | UNSUPPORTED, кроме строгого GT subset | UNSUPPORTED |

Целевая конфигурация всех адаптеров: Minecraft **1.20.1 + Forge**. Иные версии требуют отдельного адаптера, а не изменения единственного глобального pack_format.

- Datapack: `datapacks/recipe_editor_<profileId>/pack.mcmeta` с format 15; `data/<namespace>/recipes/<path>.json`. CREATE и UPDATE сохраняют ID и полный принятый JSON. Приоритет datapack определяет фактическое переопределение в игре. DELETE отклоняется; фиктивный рецепт удаления не создаётся. Format 15 подтверждён официальным [release note Minecraft 1.20](https://feedback.minecraft.net/hc/en-us/articles/16499677456781-Minecraft-Java-Edition-1-20-Trails-Tales).
- KubeJS: только точная подтверждённая версия `2001.6.4-build.120`. Файл `kubejs/server_scripts/recipe_editor_<profileId>_generated.js`; один обработчик `ServerEvents.recipes`, точный ID filter для remove, `event.custom(JSON.parse(<экранированный JSON>)).id(<ID>)` для добавления. UPDATE удаляет старый ID и добавляет поддержанный JSON. Количества, теги и строки остаются данными. Между разными скриптами приоритет и поздние изменения не контролируются редактором. Использованы [официальные рецепты KubeJS](https://kubejs.com/wiki/tutorials/recipes/); сигнатуры custom/remove/id дополнительно сверены через javap с установленным JAR .120 без загрузки кода модов.
- CraftTweaker: только `14.0.57`, релиз Forge 1.20.1 подтверждён [публикацией автора](https://modrinth.com/mod/crafttweaker/version/14.0.57). Файл `scripts/recipe_editor_<profileId>_generated.zs`. Crafting serializers используют manager `minecraft:crafting`, оба smithing — `minecraft:smithing`, остальные известные Vanilla serializers — одноимённый manager. Генерация использует `MapData`, `addJsonRecipe` и `removeByName`. `*` В [исходном коде IRecipeManager 1.20.1](https://raw.githubusercontent.com/CraftTweaker/CraftTweaker/1.20.1/Common/src/main/java/com/blamejared/crafttweaker/api/recipe/manager/base/IRecipeManager.java) addJsonRecipe строит ResourceLocation с namespace CraftTweaker. Поэтому CREATE/UPDATE допустимы только для `crafttweaker:*`, DELETE — по полному исходному ID. Смена manager при UPDATE отклоняется. [Документация manager](https://docs.blamejared.com/1.20/en/vanilla/api/recipe/manager/IRecipeManager/) используется вместе с исходниками выбранной ветки. CraftTweaker в реальной проверяемой сборке отсутствует; установленный runtime и ZenScript compiler не запускались.

## GTCEu 7.1.4

Изучены указанные пользователем [ветка 1.20.1](https://github.com/GregTechCEu/GregTech-Modern/tree/1.20.1) и [документация рецептов](https://gregtechceu.github.io/GregTech-Modern/1.20.1/Modpacks/Recipes/Adding-and-Removing-Recipes/). Текущая ветка отличается от установленной 7.1.4, поэтому примеры builder API не перенесены вслепую. Структура GTRecipeSerializer, Content, SizedIngredient, FluidIngredient и EnergyStack сверена статически с `gtceu-1.20.1-7.1.4.jar` через javap.

Адаптер принимает только полные JSON assembler/macerator/chemical_reactor. Он сохраняет JSON без преобразования в предположительный builder. Для каждой группы свойств capability содержит самостоятельный статус:

| Свойство | Поддержка |
|---|---|
| Recipe Type | Три указанных типа |
| Item Inputs / Outputs | item/tag, alternatives, gtceu:sized с count/ingredient |
| Fluid Inputs / Outputs | amount и value.fluid/value.tag |
| EU/t | tick capability eu: voltage, amperage |
| Duration | Положительное целое |
| Recipe Conditions | Только отсутствие/пустой список; непустые UNSUPPORTED |
| Chance Outputs | UNSUPPORTED; допускается только гарантированный content без boost |
| Additional Properties / Research | UNSUPPORTED; непустой data и неизвестные поля блокируются |

Неполные outputs, нулевые/некорректные количества, неверный тип чисел, неизвестные capabilities и машинные типы отклоняются. CREATE/UPDATE/DELETE через KubeJS допускаются только после этих проверок, включая полный исходный JSON для удаления. Частичные сведения и динамические рецепты не восстанавливаются. В реальном GT JAR не обнаружены статические recipe JSON для такого end-to-end теста; проверка адаптера основана на codec fixtures и статическом API. **Полная поддержка GregTech и runtime-регистрация не заявляются.**

## Запись, конфликты и восстановление

1. Проверяются canonical output root, source consent, безопасные относительные пути Windows и собственный namespace профиля. JAR никогда не является выходным или stale-путём. Обнаруженные symlink/junction-перенаправления отклоняются.
2. Берётся блокировка output-каталога. Все тексты записываются в уникальный staging-каталог, данные flush/force, SHA-256 проверяются. Ожидаемые старые хеши проверяются до применения и непосредственно перед каждой операцией.
3. Перед изменением существующего файла создаётся рабочая копия `old/`, затем файл заменяется atomic move, если доступно, либо обычным move. Неизменившиеся output-файлы не заменяются. Устаревшие пути удаляются только при совпадении хеша из manifest этого профиля.
4. После файлов сохраняются резервные копии `.recipe-editor-backups/<transaction>/...` и metadata. Обновление metadata защищено отдельной блокировкой проекта от потери параллельных commit. При ошибке metadata output откатывается.
5. При сбое уже применённые операции откатываются в обратном порядке. Конкурирующее пользовательское изменение не перезаписывается откатом; возвращается RECOVERY_REQUIRED, stage/journal/old сохраняются. После обычного успеха/полного отката stage удаляется. Lock-файлы могут остаться, блокировки ОС освобождаются.

Полной транзакционной атомарности между множеством файлов и отдельным project metadata нет. При kill/power loss возможен промежуточный набор файлов. `journal.txt` хранит целевой каталог и ожидаемые старые хеши; `old/` — копии затронутых старых файлов. Автоматический replay при старте не реализован. При ручном восстановлении нужно остановить конкурирующие записи, сверить файлы/manifest/backup и сохранить внешние правки перед восстановлением. Это не ОС-песочница против активной подмены на каждой файловой операции.

Лимиты: до 10000 выбранных изменений, 16 MiB входного replacement JSON, 10000 выходных файлов / 48 MiB суммарно; отдельный recipe JSON ограничен существующими 2 MiB. Export metadata ограничена 16 MiB, 128 профилями/manifest, 10000 путями/ID на manifest. Большие наборы можно разделять на независимые профили. Проверен набор из 1000 CREATE с одним общим KubeJS-скриптом.

## Интерфейс и использование

Запуск:

```powershell
.\gradlew.bat :app:run
```

Import/Open Project → Recipes → Save Project → **Export All Changes**. Для одного рецепта — **Export Recipe** в контекстном меню. Мастер содержит профили, имя, каталог, AUTO/явный формат, overwrite policy, отдельное source consent, счётчики реальных изменений, выделение и source Mod / Recipe Type filters, план с альтернативами и индивидуальным format override, содержимое файлов и подробную диагностику. Dry Run/Preview не записывают даже профиль. Save Profile — отдельная явная запись. Перед Export требуется просмотр preview; ошибки блокируют Export. Отмена допустима до применения, прогресс показывает переход к неотменяемой стадии. После успеха можно открыть каталог результата.

Профиль заменяет весь свой прошлый набор: частичный повторный выбор сокращает общий скрипт и может очистить старые собственные файлы. Preview об этом предупреждает; независимым наборам нужны разные профили. Политика OWNED_UNCHANGED не разрешает перетирать пользовательские изменения. Для world datapack нужно перенести `datapacks/recipe_editor_<id>` в папку datapacks нужного мира; каталога datapacks в корне Minecraft недостаточно. KubeJS/scripts устанавливаются в соответствующий game root только при наличии совместимых зависимостей.

## Проверки и фактические результаты

- `./gradlew.bat clean build`: **BUILD SUCCESSFUL**, чистая сборка всех модулей.
- `./gradlew.bat test`: **BUILD SUCCESSFUL**.
- Последующие исправления preview/metadata повторно проверены `./gradlew.bat build`: **BUILD SUCCESSFUL**.
- Обычный итоговый suite: **189 tests, 0 failures, 0 errors, 6 skipped (183 passed)**. Пропуски: четыре opt-in проверки реальной сборки и два symlink-теста без разрешения ОС. Новый модуль exporters: 54 теста, включая параметризованные варианты; один symlink case skipped.
- `./gradlew.bat test '-PrealModpack=G:\PrismLauncher\instances\Cosmic Frontiers'`: **BUILD SUCCESSFUL**, opt-in проверки реальной сборки выполнены. Экспорт actual static recipe `ae2netanalyser:analyser` из `AE2NetworkAnalyzer-1.20-1.0.6-forge.jar` прошёл во временный каталог, исходный SHA-256 JAR совпал. Артефакт — `app/build/real-export-result.txt`. Оригинальный инстанс не изменялся.
- `./gradlew.bat :app:run`: приложение действительно запущено, главное окно визуально и через accessibility наблюдалось с панелью Phase 5 / Export All Changes. Предупреждение JavaFX об unnamed module сохранено от предыдущих фаз и не препятствует запуску.
- Полная ручная проверка Export Wizard не выполнена: пользователь взаимодействовал с тем же окном, автоматический ввод прекращён. Не заявляется, что все UI-переходы проверены вручную. Генераторы, preview/dry run, применение, повторный экспорт, конфликты, manifest и rollback проверены автоматическими тестами.
- Minecraft/Forge server, KubeJS runtime и ZenScript compiler **не запускались**. Ни один скрипт не объявляется runtime-проверенным.

Новые тесты:

- DatapackRecipeExporterTest: все 9 serializers, pack.mcmeta, ID/path, UPDATE, отказ DELETE, unknown fields/category, отсутствие записей при Dry Run.
- KubeJsRecipeExporterTest: CREATE/UPDATE/DELETE, общий стабильный скрипт, ID/tag/count, экранирование quote/backslash/newline/U+2028/U+2029, неизвестные версии и custom ingredients.
- CraftTweakerRecipeExporterTest: managers, namespace restriction, imports, JSON data literals/float, добавление/удаление/обновление, несовместимости, безопасные строки и отказ null/oversized integer.
- Gtceu714AdapterTest: полный codec subset, fluid/item/EU/duration, отдельные свойства, отказ chances/research/conditions/incomplete outputs/unknown machine/version.
- ExportPlannerTest: AUTO, alternatives/override, no dependency, unsupported, conflicting profile, точное выделение, explicit copy, source hash, 1000 изменений.
- ExportFileWriterTest: stage/create, owned overwrite/backups, отказ user file/stale preview, corrupt stage, частичный apply, metadata failure, rollback/recovery conflict, cancellation, traversal/device/ADS paths, source consent, symlink (conditional).
- ExportManifestTest: round trip/checksum, re-export stable paths, один актуальный manifest, ручные правки, stale cleanup, REFUSE.
- ExportIntegrationTest: тестовый импорт → saved project → materialize → план → три формата → тексты/manifest → неизменный JAR; несохранённое изменение исключено. Отдельный opt-in case — реальная сборка, только чтение.

## Отдельная runtime-матрица для тестового инстанса

Эти проверки предусмотрены как **ручные интеграционные сценарии и не выполнялись**. Автоматического Minecraft harness/Companion пока нет. Обычные JUnit-тесты Minecraft не запускают.

Использовать отдельную копию инстанса/мира с Minecraft 1.20.1 + Forge, подтверждёнными версиями зависимостей и сохранёнными логами. Каждый сценарий запускать отдельно: Datapack через reload/включение pack, KubeJS через полноценный перезапуск/reload recipes, CraftTweaker через перезапуск/поддерживаемый reload. Проверять результат после загрузки в игровом RecipeManager/просмотрщике и фактическим выполнением рецепта, а не только отсутствием ошибки создания файла. Для регистрации serializer/менеджера могут требоваться restart и совместимые моды.

| Сценарий | Ожидаемая проверка в игре |
|---|---|
| Datapack CREATE/UPDATE каждого Vanilla типа | Точный ID, ingredient/tag/count, smithing behavior, cooking time/experience; переопределённый рецепт соответствует JSON |
| KubeJS CREATE/UPDATE/DELETE | Новый ID присутствует, старый ID удалён либо заменён; нет duplicate/serializer errors; значения не изменены |
| CraftTweaker CREATE/UPDATE/DELETE | ZenScript компилируется; правильный manager; crafttweaker namespace; полное удаление заданного ID |
| GTCEu subset | Полный подтверждённый JSON реальной машины, item/fluid/EU/t/duration сохранены; при отсутствии такого JSON тест BLOCKED, данные не придумывать |
| Порядок загрузки | Повторить с чужим скриптом/другим datapack, зафиксировать победителя; файл экспорта сам по себе этого не доказывает |
| Повторный экспорт | В тестовой копии обновлённый набор загружается, старые собственные операции исчезли; пользовательские файлы сохранены |

Runtime-подтверждение должно привязываться к версиям зависимостей, ID профиля и SHA-256 конкретных сгенерированных файлов. Текущий manifest намеренно хранит `GENERATED_RUNTIME_UNVERIFIED`. Это позволяет позднее добавить проверяющий адаптер/Companion без смешения генерации и подтверждения; автоматического перехода к Phase 6 нет.

## Изменённые и созданные файлы

Полный перечень исходников/Gradle/документации: [PHASE5_FILES.txt](PHASE5_FILES.txt). Существующие файлы: exporters/build.gradle.kts, desktop-ui/build.gradle.kts, RecipeEditorApplication.java, MainWindow.java, RecipeBrowserView.java, exporters/package-info.java, README.md. Добавлены контракты и реализации exporters, восемь файлов тестов exporters (включая fixture), ExportWizard.java, app ExportIntegrationTest.java, этот отчёт и перечень файлов.

Дополнительно создан отдельный ручной UI fixture `.manual-tests/phase5/` с явными именами TEST_INSTANCE / TEST_FIXTURE / TEST_PROJECT. Он не является играбельной сборкой, не содержит исполняемых модов и не подмешивается в реальные данные приложения. Полный ручной прогон на нём не выполнялся. Gradle build outputs/логи — сгенерированные артефакты, в список исходников не включены.
