# Minecraft Recipe Editor

Настольное приложение для Windows x64, Java 21 и JavaFX. Реализованы основа проекта, импорт сборок, статическое индексирование Minecraft 1.20.1, система редактирования рецептов через отдельный проект и экспорт сохранённых изменений и локальная runtime-интеграция (Phase 6) и версионный GregTech профиль (Phase 7, Step 1–2). Основная проверенная конфигурация — Forge 1.20.1.

## Сборка и запуск

Нужны JDK 21 (Desktop/корневой Gradle) и JDK 17 (общие wire-модули/Companion); JAVA_HOME указывает на JDK 21. Первый запуск Gradle Wrapper требует доступ к Maven Central / services.gradle.org. Используется Gradle 9.2.0, устанавливать системный Gradle не нужно.

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat :app:run
```

Перед clean закройте приложение: Windows блокирует используемые JAR. JavaFX использует механизм classpath из Phase 1; предупреждение об unnamed module не мешает запуску. Другие ОС и Windows ARM пока не поддерживаются.

## Начало работы

Для нового проекта нажмите **Import Modpack** и выберите отдельный инстанс (например, внутри `G:\PrismLauncher\instances`), а не общий каталог instances. После импорта запускается фоновый анализ. **Index resources** повторяет статический анализ импортированных источников; для обновлённого набора модов выполните импорт заново или Open Project.

До runtime-синхронизации каталоги Items / Blocks / Fluids показывают статически предполагаемые ресурсы. После совместимого merge подтверждённые записи получают CONFIRMED_BY_RUNTIME; остальные сохраняют статическую достоверность. Namespace и Source Mod фильтруются раздельно. Выбор en_us/ru_ru относится к ресурсам, язык UI не меняет. View Recipes / View Uses используют эффективные разобранные ссылки предметов и жидкостей; при отсутствии таких ссылок кнопки отключены. Совпадения по тегам остаются кандидатами.

## Редактирование — Phase 4

### Resource Browser и Materials

Items / Blocks / Fluids используют карточки фиксированного размера, виртуализированные строки и ленивые превью. Полное имя и ID доступны в Tooltip и Details. Справа показаны confidence, runtime confirmation, фактический translation key и физический источник текстуры. Выбор ru_ru использует en_us как fallback, затем ID; шаблон перевода без аргументов не считается названием.

Разрешаются цепочки моделей, texture variables, generated layers, block item parents, variants/multipart как образцы. `.png.mcmeta` задаёт первый/указанный кадр. Runtime Companion добавляет реальные baked-model sprite references, fluid still/flowing/source references и tint. При доступном SHA256 активной PNG несовпадение со статическим файлом блокирует превью. Это не полный Minecraft renderer: biome-dependent tint без позиции, custom geometry и generated atlas sprites без доступной PNG могут оставаться неподдержанными. Причина показана в Details/Tooltip.

**Materials** требует полного runtime-снимка с расширением `materials-v1`. Реализован read-only collector для проверенного **GTCEu `8.0.0-SNAPSHOT+e24b238`**: Material Registry, properties/flags/components, реальные registered item/block/fluid forms. Другие сборки не считаются ABI-совместимыми автоматически; причина доступна в Runtime diagnostics. Материал не является предметом; изображение материала — образец его зарегистрированной формы. Нажатие формы открывает соответствующий ресурс. Поддержка материалов GT8 не включает специализированное редактирование его рецептов.

Wire protocol остаётся v1: новый запрос согласуется через capabilities, со старым Companion каталог материалов недоступен. Snapshot schema v2 добавляет `materials.jsonl`; schema v1 по-прежнему читается. Старый Desktop не сможет открыть v2-снимок. После обновления Companion перезапустите Minecraft и выполните Runtime → Synchronize; старые снимки не получают новые texture/component/material metadata автоматически.

Реальные замеры до/после, ограничения и команды воспроизведения: [RESOURCE_BROWSER_REPORT.md](RESOURCE_BROWSER_REPORT.md).

Откройте **Recipes**. Исходные записи сохраняются неизменными; пользовательские изменения накладываются через ChangeSet. При одинаковом ID в нескольких JAR исходные определения остаются доступны; одно проектное UPDATE перекрывает все эти кандидаты, о чём редактор предупреждает. Фактический победитель в Minecraft не угадывается.

- **Create**: задайте `namespace:path`, затем тип рецепта. Для известных типов откроется специализированная форма. Пустая форма не содержит вымышленных ингредиентов и требует заполнения до Save.
- **Edit**: откройте выбранный рецепт или дважды нажмите строку. ID существующего рецепта не переименовывается; используйте Duplicate.
- **Visual**: shaped grid 3×3 (нажатие — выбор/замена, контекстное меню — очистка), shapeless list (добавление/изменение/удаление), cooking/stonecutting ingredient, smithing template/base/addition. Пустые слоты не являются ингредиентами. При сериализации shaped удаляются пустые внешние границы, ключи назначаются автоматически.
- **IngredientPicker**: поиск по имени/ID, Mod-фильтр, item или tag, известные теги, ручной корректный ID, альтернативы. Предупреждение о статической достоверности не блокирует выбор. Vanilla input занимает один слот; для нескольких предметов повторите слоты/элементы списка. Компонент поддерживает выбор fluid references для будущих адаптеров, но фиктивного Vanilla fluid input нет.
- **Result**: один фиксированный результат и его количество, удаление/повторный выбор. После удаления результат отсутствует только в черновике, Save блокируется до исправления. Trim имеет динамический результат и не предлагает вымышленный фиксированный ItemStack.
- **Advanced**: group и, для cooking, experience/cookingtime. Поля задаются JSON-значениями (group — строка в кавычках), Apply обновляет общий черновик. Не применённые поля сохраняются при переключении вкладок и блокируют Save, пока их не применили или не сбросили.
- **Raw JSON**: редактирование, Format JSON, Validate / Apply JSON, ошибки. Повторный разбор выполняется в одном фоновом worker с debounce. Пока новый текст не проверен, старое визуальное состояние недоступно. Неизвестные поля сохраняются при патчах известных полей; явное изменение Raw JSON меняет ровно то, что ввёл пользователь.
- **Diff**: структурное сравнение с исходным определением, пути входов, результатов, количеств и параметров, включая неизвестные JSON-поля. Пробелы и порядок ключей не считаются семантическим изменением. Для исходных конфликтов baseline — явно выбранный оригинал, либо первый кандидат при открытии уже сохранённого override; все base references сохранены.
- **Save to project** в диалоге применяет проверенный черновик к ChangeSet **в памяти**. **Reset draft** возвращает последний сохранённый в диалоге текст. Закрытие с несохранёнными/неприменёнными полями требует подтверждения.
- **Duplicate** запрашивает уникальный ID, создаёт независимое CREATE из эффективной версии и открывает его для дальнейшего редактирования.
- **Delete** после подтверждения добавляет логическое DELETE. Оригинальные файлы не удаляются. CREATE → DELETE сворачивается в отсутствие изменения. Удалённые оригиналы видны в фильтре DELETED, восстановление — Reset Changes или Undo.
- **Reset Changes** удаляет сохранённое проектное изменение ID; действие поддерживает Undo.

Статусы рядом с ID: `[+] CREATED`, `[M] MODIFIED`, `[-] DELETED`, `[!] SOURCE CONFLICT`. Отдельно показан статус разбора JSON. Есть фильтр статусов и счётчики изменений. Контекстное меню включает Open / Edit / Duplicate / Delete / Reset / Copy ID / Copy JSON / Show Source; недоступные действия отключены с пояснением.

Undo/Redo работают для Create/Update/Delete/Duplicate/Reset: Ctrl+Z, Ctrl+Y, Ctrl+Shift+Z и кнопки каталога. Когда фокус находится в текстовом поле, сочетания остаются у его текстовой истории. Новое проектное действие после Undo очищает Redo. История ограничена 200 командами / оценкой 32 MiB удерживаемых JSON и не сохраняется после перезапуска.

## Сохранение и открытие проекта

**Save Project** записывает отдельный `project.json` в выбранное место **за пределами исходной сборки**. Формат 1 содержит source directory, ChangeSet, исходные source references и SHA-256 содержимого изменяемых рецептов. Контрольная сумма payload проверяет целостность файла; это не цифровая подпись.

Запись: сериализация/валидация → временный файл в целевой папке → flush → резервная копия предыдущего корректного файла `project.json.bak` → атомарная замена, если поддерживается. При отсутствии atomic move применяется обычная замена с сохранённой резервной копией. Если сериализация не удалась, рабочий файл не заменяется. Повреждённый существующий проект не перезаписывается молча. Open Project позволяет открыть .json или .bak для явного восстановления.

**Open Project** загружает файл в фоне, повторно импортирует указанную сборку и восстанавливает эффективные рецепты. Изменённые, исчезнувшие или новые конфликтующие источники блокируют применение соответствующего изменения. Оно сохраняется в карантине, не пропадает при Save Project и доступно через **Source conflicts** (включая исчезнувший Recipe ID). Диалог показывает сохранённые/current references и сохранённый JSON. Можно скопировать данные и явно Discard saved change; автоматического небезопасного rebase нет.

При закрытии/смене проекта с незаписанным ChangeSet доступны Save Project / Discard / Cancel. Изменения формы, ChangeSet в памяти, файл проекта и экспорт — отдельные состояния. Сохранение проекта само по себе не меняет игру. Экспорт использует повторно загруженный сохранённый файл проекта.

Версии проекта проверяются до применения данных. `ProjectFormatMigration` — явная точка регистрации доверенных миграций приложения; по умолчанию поддерживается формат 1, готовых миграций старых форматов нет. Неизвестный формат без зарегистрированного перехода отклоняется, файл при загрузке не переписывается.

## Экспорт — Phase 5

1. Выполните **Save Project**. **Export All Changes** открывает **Recipe Export Wizard**; если проект ещё не имеет файла, сначала предлагается его сохранить. Мастер повторно загружает сохранённые изменения. Последующие несохранённые правки не входят в экспорт.
2. Создайте профиль или выберите сохранённый. Укажите существующий абсолютный каталог вывода, например отдельную папку разработки вне исходной сборки. Каталог вывода — корень пакета: под ним создаются `datapacks/`, `kubejs/` и/или `scripts/`. Для Vanilla datapack папку `datapacks/recipe_editor_<profileId>` нужно установить в `saves/<world>/datapacks/` или серверный `<world>/datapacks/`; сам корень Minecraft не загружает datapacks автоматически.
3. Выберите AUTO либо конкретный формат. Для тысяч изменений используется один фоновый worker; UI остаётся доступен. Вкладка **Changes** поддерживает множественное выделение Ctrl/Shift, **Select all visible**, фильтры Mod и Recipe Type. Mod определяется по исходным source references, а не namespace. У CREATE без данных о происхождении мод-источник не угадывается.
4. Нажмите **Dry Run** или **Preview**. Обе команды только читают источники и формируют план/содержимое в памяти: не создают каталоги, output-файлы, manifest или профиль. Посмотрите **Plan / format choice**, **Generated files**, **Diagnostics**. Контекстное меню строки Plan позволяет явно выбрать другой формат для этого ID, затем нужно пересобрать Preview. AUTO показывает все совместимые альтернативы.
5. Неподдерживаемая операция блокирует весь выбранный набор. Она остаётся в плане и диагностике. Можно явно изменить формат либо исключить её в Changes и повторить Preview. Нет молчаливого частичного экспорта.
6. По умолчанию `REFUSE` запрещает менять/удалять отличающиеся старые файлы. `OWNED_UNCHANGED` разрешает замену только файлов текущего профиля, чьи хеши совпадают с manifest; резервное копирование включено. Пользовательские и изменённые вручную файлы блокируют экспорт при любой политике. В Diagnostics перечислены старые хеши и удаляемые устаревшие файлы.
7. Отметьте просмотр файлов и нажмите **Export**. Перед записью план и источники проверяются повторно. Отмена доступна до применения файлов; при применении она отключена. **Open result directory** доступна после успеха. Результат означает генерацию файлов с manifest, а не подтверждение загрузки в Minecraft.
8. **Save Profile** отдельно сохраняет профиль. После экспорта профиль и manifest сохраняются автоматически рядом с проектом в `<project>.exports.json` (версия 1, контрольная сумма SHA-256). Изменять формат project.json из Phase 4 не требуется. Имена output-файлов стабильны и содержат ID профиля; повторный экспорт не плодит скрипты.

**Один рецепт:** Recipes → контекстное меню → **Export Recipe → AUTO / DATAPACK / KUBEJS / CRAFTTWEAKER**. Для изменённого рецепта используется сохранённая эффективная версия, включая DELETE. Если сохранённого изменения нет, предлагается явное подтверждение экспорта неизменённой копии с исходным ID; ChangeSet не меняется. Несохранённый новый/изменённый рецепт и неоднозначный оригинал нельзя выдать за неизменённую копию. Duplicate с новым ID после Save Project экспортируется как CREATE.

**Профиль заменяет весь предыдущий набор своего экспорта.** Если ранее в общем скрипте были A+B, а теперь выбрано только A, после перезаписи в скрипте останется A. Независимым наборам нужны разные профили. Устаревшие файлы удаляются только в собственном namespace профиля и при совпадении хеша; изменение выбора явно предупреждается в Preview.

**Исходная сборка:** запись под импортированным каталогом заблокирована, пока пользователь явно не включит `Allow writing into the imported modpack for this export`. Это согласие действует только в текущем окне и не сохраняется в профиле. JAR не модифицируются. Для первой проверки рекомендуется отдельный каталог и затем отдельный тестовый инстанс.

| Тип Minecraft 1.20.1 / Forge | Datapack | KubeJS .120 | CraftTweaker 14.0.57 |
|---|---|---|---|
| Crafting Shaped | CREATE, UPDATE | CREATE, UPDATE, DELETE | CREATE*, UPDATE*, DELETE |
| Crafting Shapeless | CREATE, UPDATE | CREATE, UPDATE, DELETE | CREATE*, UPDATE*, DELETE |
| Smelting / Blasting / Smoking / Campfire | CREATE, UPDATE | CREATE, UPDATE, DELETE | CREATE*, UPDATE*, DELETE |
| Stonecutting | CREATE, UPDATE | CREATE, UPDATE, DELETE | CREATE*, UPDATE*, DELETE |
| Smithing Transform / Trim | CREATE, UPDATE | CREATE, UPDATE, DELETE | CREATE*, UPDATE*, DELETE |
| GTCEu assembler / macerator / chemical_reactor | Нет | Ограниченный статический адаптер 7.1.4 | Нет |
| Неизвестные типы / частичный JSON / custom ingredients | Нет | Нет | Нет |

`*` CraftTweaker `addJsonRecipe` создаёт namespace `crafttweaker`; поэтому CREATE/UPDATE поддерживают только такие ID. DELETE использует полный исходный ID. Смена Recipe Manager при UPDATE отклоняется. Это проверка генерации и API, а не runtime-гарантия. Для произвольного namespace используйте Datapack/KubeJS.

KubeJS проверяет точную версию `2001.6.4-build.120`. CraftTweaker — `14.0.57`; отсутствующая/неизвестная/другая версия отклоняется. Datapack использует `pack_format=15` только для адаптера Minecraft 1.20.1. DELETE в обычном datapack не поддержан: фиктивный JSON удаления не создаётся. Скрипты группируются по профилю и формату; KubeJS сохраняет JSON как данные через `JSON.parse`, не вставляет пользовательский код. Неизвестные поля, условия, частично разобранные рецепты не маскируются под поддержанные.

GTCEu 7.1.4: typed profile для assembler / macerator / chemical_reactor, item/tag/sized ingredients, fluid/tag/amount, tick EU, duration, chance/boost и non-consumable inputs. KubeJS использует GT schema factory и проверенный codec merge. Непустые conditions/data/research, неизвестные поля и неподтверждённые custom codecs блокируют export без потери исходного JSON. Реальный CREATE/UPDATE/DELETE roundtrip проверен для assembler на integrated server; отдельные runtime проверки двух других машин не заявлены. Подробная матрица и ограничения — Phase 7 ниже.

Запись выполняется через staging, проверку хешей, резервные копии и замену файлов; при сбое восстанавливаются уже применённые output-файлы. Обнаруженные символьные ссылки/junction-перенаправления и Windows special paths отклоняются. Перезапись вне namespace профиля невозможна. Резервные копии: `.recipe-editor-backups/<transaction>/...`; lock-файлы `.recipe-editor-export.lock` и `<project>.exports.json.lock` могут оставаться после экспорта; сами блокировки ОС освобождаются после операции. Нельзя обеспечить одну атомарную операцию для множества файлов и metadata: при аварийном завершении процесса/питания нужен ручной разбор `.recipe-editor-stage-*/journal.txt` и `old/`. При конфликте во время отката возвращается **RECOVERY_REQUIRED**, данные восстановления сохраняются. Конкурирующее внешнее изменение не перетирается откатом. Это защита от обычных файловых конфликтов; активная подмена файлов другой программой на каждой инструкции не является гарантированно изолированной файловой системой.

Подробные архитектурные решения, список файлов, источники API и результаты проверок: [PHASE5_REPORT.md](PHASE5_REPORT.md).

## Поддержка и ограничения

Известные типы 1.20.1: crafting_shaped, crafting_shapeless, smelting, blasting, smoking, campfire_cooking, stonecutting, smithing_transform, smithing_trim. Поддерживаются item/tag/alternative ingredients, количество результата, pattern/key, cookingtime/experience, роли smithing. Vanilla types не получают произвольные multiple outputs или probability. Forge custom fields и вложенные данные нетронутого результата сохраняются; их семантика может остаться PARTIALLY_PARSED.

**Неизвестные рецепты поддерживаются ограниченно — только Raw JSON с проверкой синтаксиса, ID и типа. Это не полноценная семантическая поддержка кастомного формата.** Для GTCEu 7.1.4 добавлен специализированный редактор и ограниченный export profile (Phase 7 ниже). Create, Mekanism и непроверенные recipe conditions требуют отдельных адаптеров.

Runtime Companion и snapshot-scoped подтверждение registry реализованы в Phase 6. Пока не реализованы: выполнение пользовательского Java-кода/скриптов внутри Desktop, полный рендер Minecraft, drag-and-drop сетки, persistent discovery cache, история Undo после перезапуска, автоматическое объединение source conflicts и переезд проекта с автоматическим поиском перемещённой сборки. Для восстановления по текущему пути требуется доступность исходного инстанса. Редактирование недоступно без подтверждённых метаданных Minecraft 1.20.1.

Статический анализ остаётся неполным: программные ресурсы/рецепты могут отсутствовать, Forge conditions/datapack load order не вычисляются, tag membership объединяет статически возможные значения. Все источники конфликтующих Recipe ID сохраняются. Model JSON, текстура и локализация не подтверждают регистрацию. Статические жидкости обнаруживаются только по явным fluid-tag references; runtime-каталог поступает из реестров Companion. Общие vanilla client JAR лаунчера за пределами инстанса не сканируются. Превью показывают плоские слои или явно помеченный образец текстуры. Сложная геометрия имеет RENDERING_UNSUPPORTED; неизвестный порядок переопределений — AMBIGUOUS_SOURCE, произвольный победитель не выбирается.

## Архитектура

Десять модулей сохранены, циклов нет:

```text
app -> desktop-ui, core, modpack-parser, registry, recipe-engine,
       recipe-analyzer, integrations, exporters, companion-protocol, companion-client
core -> нет внутренних зависимостей
modpack-parser -> core
registry -> core
recipe-engine -> core, registry
recipe-analyzer -> recipe-engine, registry, companion-protocol, integrations
integrations -> core, companion-protocol, recipe-engine
exporters -> core, recipe-engine, integrations
companion-protocol -> Gson (Java 17)
companion-server -> companion-protocol (Java 17)
companion-client -> companion-protocol, recipe-engine
desktop-ui -> core, recipe-engine, exporters, companion-client, integrations
```

`recipe-engine.editor` содержит immutable change records, сессию, команды/историю, валидатор, структурный diff, JSON-tree patch service и incremental EffectiveRecipeIndex. Он не зависит от UI или реализации хранения. `app.project` — единственный модуль управления хранением проектов в текущей архитектуре; новая отдельная Gradle-модель проекта не создавалась. Он реализует контракт ProjectPersistence, а app связывает version-specific parsers и UI. `desktop-ui` только отображает состояние и вызывает сервисы; чтение/запись файлов, JSON-парсинг, валидация и проектные команды выполняются в фоновых workers.

## Безопасность и лимиты

Исходные JAR/сборки только читаются. Архивы не исполняются и не извлекаются. Пути проверяются через real path относительно выбранного корня; выходящие за него ссылки отклоняются. Это не ОС-песочница против параллельной подмены другим процессом.

Phase 2: до 4096 JAR, 256 MiB на архив, 50000 записей, 512 KiB на metadata, 64 MiB metadata на импорт. Phase 3: 150000 resource references, 2 MiB JSON, 192 MiB JSON суммарно, глубина 64; до 100000 записей/256 MiB на архив. Локальные источники: datapacks, config/openloader/data, kubejs/data/assets и максимум 64 непосредственных worlds/datapacks, 256 datapacks на коллекцию, глубина 24 / 200000 посещённых элементов. PNG: до 2 MiB, сторона до 2048, максимум 1048576 пикселей. Превью читаются по требованию одним worker, очередь 128, LRU 64 PNG.

Phase 4: редактируемый JSON до 2 MiB UTF-8, проект до 48 MiB и 10000 изменений. Контроль размера применяется до записи и в ограниченном сериализующем потоке. Работа с ошибочным JSON не меняет ChangeSet. Длинные значения в визуальной форме сокращаются для отзывчивости; полный текст остаётся в Raw JSON.

## Проверки

Полный статический сценарий Phase 5 с реальной сборкой (только чтение инстанса; вывод во временный каталог):

```powershell
.\gradlew.bat :app:test --tests '*ExportIntegrationTest' '-PrealModpack=G:\PrismLauncher\instances\Cosmic Frontiers'
```

Отчёт — `app/build/real-export-result.txt`. Это проверка генерации, не Minecraft runtime. Матрица ручных runtime-проверок для отдельного тестового инстанса описана в PHASE5_REPORT.md; Runtime Companion реализован отдельно в Phase 6; этот тест экспорта его не запускает.


```powershell
.\gradlew.bat :app:test --tests '*RealRecipeEditingTest' '-PrealModpack=G:\PrismLauncher\instances\Cosmic Frontiers'
```

Этот opt-in тест импортирует реальную сборку, редактирует рецепт только в памяти, проверяет Undo/Redo/duplicate/delete, сохраняет проект только во временный каталог, восстанавливает его и проверяет source conflict. SHA-256 исходного JAR сверяется до и после. Отчёт — `app/build/real-editor-result.txt`. Обычные тесты не требуют Minecraft и используют временные файлы.

Логи: консоль и `%USERPROFILE%/.minecraft-recipe-editor/logs/application.log`, ежедневная ротация, 14 дней.

Перечень файлов и результаты: [Phase 4](PHASE4_REPORT.md). Исторические отчёты: [Phase 2](PHASE2_REPORT.md), [Phase 3](PHASE3_REPORT.md). Phase 5: [отчёт об экспорте](PHASE5_REPORT.md). Phase 6: [Runtime Companion](PHASE6_REPORT.md). Phase 7: реализованы Step 1–2 (framework и GTCEu 7.1.4); Create/Mekanism не начаты.


## Runtime Companion — Phase 6

Desktop работает на JDK 21, Forge Companion — на JDK 17. Нужны обе JDK. Для сборки мода из корня:

```powershell
.\gradlew.bat buildCompanion
```

Результат: `minecraft-companions/forge-1.20.1/build/libs/mre_companion-forge-1.20.1-0.1.0.jar`.
Задача выбирает установленный Java 17 toolchain и запускает отдельный Wrapper Gradle 8.8 / ForgeGradle 6.0.54.
Первый запуск требует доступа также к Maven Forge и Minecraft libraries. Desktop использует собственный Gradle 9.2.
Прямая сборка: перейдите в `minecraft-companions/forge-1.20.1`, установите JAVA_HOME на JDK 17 и выполните `./gradlew.bat clean build`.

1. Скопируйте только готовый JAR в `minecraft/mods` выбранного тестового инстанса Minecraft **1.20.1 Forge 47.x**. Проверено на Forge 47.4.10. Для разработки сначала используйте отдельный тестовый инстанс.
2. Запустите Minecraft на Java 17 и откройте мир: нужен активный integrated server либо локальный dedicated server с Companion. Главное меню не содержит серверных рецептов.
3. Запустите Desktop: `.\gradlew.bat :app:run`. Откройте **Runtime**, выберите `<game directory>/config/mre_companion/session.json`, нажмите **Connect**, затем **Synchronize**.
4. Session-файл содержит локальный секрет: не публикуйте его. Токен меняется при старте процесса; старый файл после перезапуска нужно выбрать/прочитать заново. Токен не выводится в UI/логи и не передаётся по сети открытым текстом.
5. Просмотрите реальные количества, среду, диагностику и реестры. **View Snapshot** открывает доступные runtime-рецепты только для чтения. В Recipes доступны фильтры **All / Static / Runtime**; редактирование сохранено для статических исходников.
6. Завершённые snapshots сохраняются в `%USERPROFILE%/.minecraft-recipe-editor/runtime-snapshots/`. **Open Snapshot** загружает `.mresnapshot` как исторические данные без подключения. При ошибке/отмене предыдущий полный snapshot сохраняется.

Merge выполняется только для открытого проекта той же локальной директории Minecraft и совместимой среды/модов. Runtime подтверждает существующие ресурсы и теги, сохраняет статические оригиналы и ChangeSet. Доказанные расхождения рецептов помещают затронутые изменения в source conflict; runtime-only данные не превращаются в редактируемый исходный JSON и не экспортируются как оригиналы. Для другой среды доступен отдельный просмотр snapshot.

Протокол — ограниченный TCP на `127.0.0.1`, версия 1, mutual HMAC challenge, последовательные запросы, chunks, SHA-256, таймауты, отмена, heartbeat. Нет удалённых команд, reload, записи файлов по сетевому запросу или изменения мира. Перезагрузка datapack инвалидирует поколение: выполните Synchronize повторно.

Проверены реальные Forge GameTest Server, клиент с integrated server (включая reload и остановку), GTCEu **7.1.4** в изолированном тестовом окружении. Реальная сборка Cosmic Frontiers и её миры не запускались и не изменялись. Обычный `test` пропускает opt-in Minecraft-проверки; отдельный запуск реального Forge:

```powershell
.\scripts\Verify-CompanionRuntime.ps1 -Java17Home 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot' -Java21Home 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
```

Скрипт запускает ограниченный по времени GameTest в собственном `run-vanilla`, не пользовательский мир. Для GT нужны локальные deobfuscated test references в параметре `-GtReferenceDirectory`; они не поставляются. Сам convenience-скрипт не был отдельно прогнан; эквивалентные Gradle-команды использованы в успешных runtime-проверках. Проверка адаптеров по сохранённому реальному snapshot:

```powershell
.\gradlew.bat :companion-client:test --tests '*RuntimeRecipeAdapterTest' '-PruntimeSnapshotFile=L:\My Add\RecipeEditor\.manual-tests\phase6\evidence\gt-runtime.mresnapshot'
```

Подробности исходного протокола и ограничений Phase 6: [PHASE6_REPORT.md](PHASE6_REPORT.md). Отдельный просмотр материалов и расширение материалов описаны в [RESOURCE_BROWSER_REPORT.md](RESOURCE_BROWSER_REPORT.md). Редактирование материалов не реализовано. Поддержка рецептов GregTech описана ниже в Phase 7.


## Phase 7 — Integration Framework и GregTech

Реализованы только Step 1–2 последнего задания. Create, Mekanism и полный Integration Manager оставлены для следующих запросов.

- `integrations.api` предоставляет registry, version/build compatibility, capabilities и отдельные mapping/runtime mapping/validation/editing/export contracts. Minecraft API остаётся в независимом Forge Companion.
- Проверенный профиль редактирования — **GTCEu 7.1.4 / Minecraft 1.20.1 Forge**. Найденная `8.0.0-SNAPSHOT+e24b238` имеет другую Content ABI и не объявлена совместимой: специализированные операции блокируются. Generic runtime metadata и Raw JSON исходных файлов сохраняются.
- Toolbar **GregTech capabilities** показывает версию и возможности импортированной среды. Это диагностический диалог, не реализация будущего Step 5 Integration Manager.
- Для статического GT JSON и проектных GT copies доступны **GregTech Recipe Editor**, Advanced, Raw JSON и Diff. Поля создаются из существующих данных: Duration, item/fluid references, quantities, tick EU, chance/maxChance/boost. Кнопка Apply изменяет только выбранное поле. Не применённые поля блокируют Save; Save повторно валидирует через общий RecipeEditorService. Undo/Redo/Save Project/экспорт используют прежний ChangeSet.
- После успешного runtime merge в Recipes → Runtime выберите GT 7.1.4 рецепт и **Copy supported GT state to NEW project recipe**. Это явная новая копия нормализованного состояния, а не восстановленный оригинал. Неоднозначные runtimeVariants отклоняются. UPDATE/DELETE runtime-only оригинала без проверяемого source reference не разрешаются; статические оригиналы используют обычные Edit/Delete.
- KubeJS профиль: **2001.6.4-build.120**, типы `gtceu:assembler`, `gtceu:macerator`, `gtceu:chemical_reactor`; фактическая runtime-матрица приведена в PHASE7_REPORT.md. Полный набор свойств каждого рецепта проверяется заново. Chance/boost и non-consumable сохраняются. Unknown/custom/research/conditions без проверенного codec profile блокируют экспорт целиком, сохраняя Raw JSON.
- GT schema factory добавляет machine prefix к ID. Поэтому экспортируемый ID должен иметь вид `recipe_editor:assembler/name`, `recipe_editor:macerator/name` либо `recipe_editor:chemical_reactor/name`. Namespace `minecraft` отклоняется из-за переименования в GT API. Генератор не меняет ID молча. UPDATE выполняет exact-ID remove перед добавлением; DELETE — exact-ID remove.
- Runtime codec может кодировать EnergyStack числом; KubeJS 7.1.4 component требует `{voltage,amperage}`. Генератор переводит scalar в эквивалентный `(voltage,1)`, подтверждённый установленным EnergyStack constructor. Представление модели/исходник остаются неизменными.
- Machine/config-specific IO limits не выдумываются: без достоверных runtime ограничений отображается предупреждение. Неизвестные properties/conditions/circuit/custom ingredients сохраняются полностью; это не обещание возможности их экспорта.

Для ручного осмотра настоящего рецепта из сохранённого snapshot без записи пользовательского проекта есть test-only preview:

```powershell
.\gradlew.bat :app:previewGregTech '-PruntimeSnapshotFile=L:\My Add\RecipeEditor\.manual-tests\phase6\evidence\gt-runtime.mresnapshot'
```

Продуктовый запуск остаётся `.\gradlew.bat :app:run`. Подробная матрица чтения/редактирования/экспорта и результаты реальных проверок: [PHASE7_REPORT.md](PHASE7_REPORT.md).


## Phase 8 — Analysis Framework, Conflicts, Dependency Graph

Реализован backend **Step 1–3** в `recipe-analyzer`; Step 4–6 и Phase 9 не начаты.

- Captured AnalysisContext содержит оригиналы, ChangeSet, версии/mods/capabilities, registry и необязательный snapshot. STATIC_PROJECT и SNAPSHOT_PROJECT разделены явно; несовместимое/устаревшее runtime evidence сопровождается предупреждениями.
- DuplicateRecipeDetector различает точные source JSON, same-ID representations, потенциальные пропорциональные эквиваленты и похожие IO. Один общий результат не означает duplicate. Runtime variants не считаются автоматически неисправными рецептами.
- RecipeConflictAnalyzer подтверждает только известный Vanilla 1.20.1 matcher overlap; GT/unknown selection rules остаются потенциальными. Неразрешённые tags не подтверждают overlap. Ограниченный candidate search явно сообщает partial result.
- Типизированный граф сохраняет recipe nodes, AND input slots, OR choices/tags, item/fluid separation, явные chemical/material adapter domains, catalyst/quantity/chance/cadence metadata. Встроенного Create/Mekanism/material extraction пока нет.
- Итеративный Kosaraju находит SCC. Циклы информационные/потенциальные, не доказательство невозможности производства. ReverseDependencyIndex даёт bounded downstream traversal; ref-counted RecipeGraphIndex переиспользует неизменённые fragments.
- Нет расчёта стоимости/энергии/доступности, оптимизатора, нового Dashboard/Graph Viewer или report exporter: эти функции относятся к Step 4–6. В текущем UI Analyzer остаётся placeholder; библиотечный API и тестовые команды описаны в отчёте.

```powershell
.\gradlew.bat :recipe-analyzer:test
.\gradlew.bat :recipe-analyzer:externalTest --tests '*AnalyzerSnapshotIntegrationTest' '-PanalyzerSnapshot=L:\My Add\RecipeEditor\.manual-tests\phase7\evidence\modified.mresnapshot'
.\gradlew.bat :recipe-analyzer:performanceTest
```

Opt-in snapshot test читает реальные ранее сохранённые Minecraft данные; он не запускает Minecraft. Stress test использует явно синтетические recipes/mod identities. Подробности, бюджеты, API пример, результаты и ограничения: [PHASE8_REPORT.md](PHASE8_REPORT.md). Файлы: [PHASE8_FILES.txt](PHASE8_FILES.txt).


## Phase 9 — Technical Audit и Test Infrastructure

Выполнены только Step 1–2. Аудит и приоритеты: [PHASE9_AUDIT.md](PHASE9_AUDIT.md). Архитектура тестов, Gradle suites и воспроизведение: [TESTING.md](TESTING.md). Результаты и release gates: [PHASE9_REPORT.md](PHASE9_REPORT.md).

`test` теперь исключает integration/external/performance tags. `integrationTest` запускает воспроизводимые межмодульные проверки без Minecraft и входит в `build`. `testReport` создаёт сводный HTML; `verifyArchitecture` проверяет зависимости модулей. Старые environment-dependent probes перенесены в `externalTest`; stress — в `performanceTest`. Нагрузочные и live tests не запускаются обычным build.

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat testReport
.\scripts\verification\Summarize-TestResults.ps1
```

Профилирование, stability improvements, security campaigns и финальные release gates относятся к Step 3–6 и в текущем запросе не выполнены. Release 1.0: **NOT READY**.


## Phase 10 — Release Foundation и Windows Packaging

Реализованы только Step 1–2. Локальная версия: **1.0.0-alpha.1**, stable release **NOT READY**. Desktop version задаётся один раз в `gradle.properties`, build metadata генерируются автоматически. Companion/protocol/project format имеют отдельные версии.

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat packageWindows
.\gradlew.bat createInstaller '-PwixHome=<absolute-path-to-WiX-3-binaries>'
.\gradlew.bat createMsiInstaller '-PwixHome=<absolute-path-to-WiX-3-binaries>'
.\gradlew.bat releaseWindows '-PwixHome=<absolute-path-to-WiX-3-binaries>'
```

Application Image: `app/build/windows/image/MinecraftRecipeEditor/MinecraftRecipeEditor.exe`. Java 21 и JavaFX включены; для запуска пользователю не нужны Gradle/IDE/системная Java. Для portable запуска нужен весь каталог image, а не один EXE. Installers: `app/build/windows/installers`; собранный alpha bundle с ZIP/EXE/MSI/Companion/SHA256/manifest: `app/build/windows/artifacts/1.0.0-alpha.1`.

Новые логи/кэши/snapshots размещаются в Windows APPDATA/LOCALAPPDATA; проекты остаются в выбранных пользователем каталогах. Подробнее: [WINDOWS_PACKAGING.md](WINDOWS_PACKAGING.md), [Release Readiness](RELEASE_READINESS.md), [PHASE10_REPORT.md](PHASE10_REPORT.md). Публичная публикация и git tags не выполняются. Step 3–8, а также ранее отложенные функции Phase 7–9 не реализуются этим запросом.

## Происхождение рецептов

Фильтры **Recipe Namespace** и **Source Mod** независимы. Source Mod означает metadata физического контейнера, а не доказанное авторство. Например, `minecraft:tnt` действительно найден в JAR Biomes O' Plenty. В Export Wizard вкладка **Sources / provenance** показывает все кандидаты, archive/internal paths, project overlay и confidence. Статический порядок сканирования не определяет активный источник; неизвестный runtime-источник показывается как UNKNOWN. Проекты сохраняют исходные ссылки и происхождение replacement; старые проекты формата 1 продолжают читаться. Подробности и результаты проверок: [RECIPE_PROVENANCE_REPORT.md](RECIPE_PROVENANCE_REPORT.md).

## KubeJS JavaScript / Data JSON

Для Minecraft 1.20.1 Forge проверены KubeJS 2001.6.4-build.120 и 2001.6.5-build.26 для Vanilla JSON serializers. Выберите Format=KUBEJS и KubeJS Export Mode:

| Mode | Применение |
|---|---|
| JAVASCRIPT | CREATE/UPDATE/DELETE в отдельном `kubejs/server_scripts/recipe_editor_<profile>_generated.js`; исходные ID сохраняются |
| DATA JSON | CREATE/UPDATE в `kubejs/data/<namespace>/recipes/<nested-path>.json`; DELETE возвращает UNSUPPORTED |
| AUTO / COMBINED | Data для совместимых additions/updates, JS для удалений/проверенных JS-only адаптеров; один механизм на Recipe ID |

Preview показывает фактический механизм, target files и отказы, Generated files — содержимое и замены/удаления файлов. Перед записью обязателен Review. Перезапись возможна только для неизменённых файлов, принадлежащих manifest профиля. Неизвестные кастомные/GT serializers не получают Data-поддержку автоматически. Подробнее: [KUBEJS_EXPORT_REPORT.md](KUBEJS_EXPORT_REPORT.md).
