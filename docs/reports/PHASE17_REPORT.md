# Phase 17 — результат шагов 1–4

## Объём и Git

Выполнены Git Workflow, аудит реестров, нормализация ресурсов и объединение каталогов. Рендеринг 3D и runtime client preview относятся к следующим шагам и не реализованы на этом этапе.

- Ветка: `feat/phase-17-unified-resources-3d-block-rendering`.
- База: `origin/master`, `81cb44f` (Phase 16 уже объединена).
- Рабочее дерево: `.manual-tests/worktrees/phase17` относительно основного проекта.
- Основное дерево содержит многочисленные пользовательские изменения Java. Они сохранены, не включены в коммиты и не заменены.
- Организация прежних отчётов перенесена отдельным коммитом `8ac1644`; отчёты находятся в `docs/reports`.
- Версия приложения остаётся `1.1.0-alpha.1`; версии Companion, протокола и формата проектов не изменены.

## Причина и исправления

`ResourceEntryBuilder` строил Block ID из любого `models/block/*.json`, а Item ID — из `models/item/*.json`. Поэтому модели частей дверей, вариантов и служебные модели становились карточками ресурсов. Теперь модель JSON является только asset definition и сохраняется вместе с источником в `ResourceAssetCatalog`. Для статического кандидата требуется независимая ссылка: blockstate, tag или операнд успешно разобранного рецепта. Статический кандидат не выдаётся за runtime-регистрацию.

Проверка на ISG выявила дополнительную проблему: совпадающие пути рецептов в разных архивах ошибочно делали превью Item неоднозначным. Например, два определения `minecraft:tnt` не означают два определения его модели. Проверка конфликтов превью теперь рассматривает только model/blockstate assets. Источники самих рецептов сохранены.

`UnifiedResourceIndex` не объединяет доменные Item и Block registries. Он формирует представление с одной карточкой на Item ID и сохраняет отдельные блоки. Идентификатор выбора включает kind и ID. Конфликтующие определения не выбираются произвольно: остаются источники и статус неоднозначности.

`BlockItemRelationships` использует только явные runtime-метаданные и согласованный snapshot capture. Companion получает Block ID через `BlockItem.getBlock()` и вычисляет наличие BlockItem по полному Item Registry. Несовпадающие Item ID и Block ID поддерживаются. Равенство ID, наличие модели и отсутствие записи в старом снимке не устанавливают связь. Противоречащие сведения дают UNKNOWN. Runtime class и block state properties сохраняются после merge и повторного открытия snapshot.

## Интерфейс

Основная навигация содержит Items, Fluids, Materials; отдельный Blocks удалён. В Items доступны:

- All: предметы и подтверждённые блоки без BlockItem.
- Items: предметы без подтверждённого признака BlockItem; статические кандидаты сохраняют свою неопределённость.
- Block Items: предметы с явным runtime-признаком BlockItem.
- Blocks without Item: только явное runtime-подтверждение отсутствия BlockItem.
- All Blocks (technical): все блоки, включая неизвестные связи старых снимков.

Фильтры Namespace и Source Mod остаются разными. Добавлены фильтры runtime/static, отсутствующей текстуры и неподдерживаемого renderer. Неизвестная текстура не считается доказанно отсутствующей.

Cards и Compact Grid используют общий существующий сервис плоских превью. Основная карточка показывает изображение и имя; ID, источники, confidence и диагностика находятся в tooltip/details. Details показывают Item/Block ID либо UNKNOWN, исходные model/blockstate файлы, свойства состояний и runtime metadata. Существующие переходы к рецептам/использованиям Item/Fluid сохранены. Формы Materials и переходы из Recipes направляют блоки в технический фильтр Items.

Список ограничен страницами по 60 записей и виртуализированными строками. Сохраняются существующие ограничения очереди, ZIP/image cache и фоновой загрузки текстур. Геометрия блоков не рендерится.

## Проверка

- `./gradlew.bat clean build`: SUCCESS, 2m 15s; проверены зависимости модулей без циклов.
- `./gradlew.bat test`: SUCCESS, отдельный вызов после чистой сборки (up-to-date).
- Unit: 337 тестов, 335 passed, 2 skipped, 0 failures/errors. Оба skip относятся к созданию symlink в Windows без необходимых прав.
- Integration: 82 passed, 0 failures/errors, включая JavaFX `UnifiedResourceBrowserTest` и прежние проверки выбора, языка и размеров окна.
- Performance: `UnifiedResourcePerformanceTest`, 50 000 Item + 20 000 Block; 1 passed, 1.265 s (JUnit elapsed, локальное измерение).
- External: `UnifiedResourceAuditTest` и `VanillaResourceLoadingTest`, 2 passed, 0 failures. Проверены настоящие ресурсы, восемь vanilla PNG и локализации. Итоговый Gradle-вызов: 42 s; импорт, анализ, загрузка snapshot и построение каталога внутри audit: 30.628 s. Эти цифры не являются гарантией времени на другом компьютере.
- Forge Companion: собственный `minecraft-companions/forge-1.20.1/gradlew.bat clean build`, SUCCESS, 2m 42s. Это проверка компиляции/JAR, не live runtime rendering; тестов в обычном source set Companion нет.
- `./gradlew.bat :app:run`: SUCCESS. Проверены главное окно, версия, отсутствие Blocks в основной навигации и штатное закрытие без проекта.
- `:app:previewResources`: SUCCESS. Вручную через Windows GUI проверены реальная сборка, Block Items, Blocks without Item, All Blocks (technical), выбор Aeronos Door, Cards → Compact Grid, поиск служебной модели (0 записей), исходный blockstate, свойства состояний и переключение en_us → ru_ru с сохранением выбора. Тестовое окно содержало диагностические кнопки ITEM/BLOCK/FLUID, это не основная навигация приложения.
- `git diff --check`: без ошибок.

Новые regression-тесты: `BlockRegistryNormalizationTest`, `BlockItemRelationshipTest`, `UnifiedResourceIndexTest`, `UnifiedResourceBrowserTest`, `UnifiedResourcePerformanceTest`, `UnifiedResourceAuditTest`; дополнен `RegistryMergeServiceTest` для сохранения связей и state properties после snapshot save/load.

### Настоящая сборка ISG

Источник: `G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh`, Minecraft 1.20.1, Forge, 71 JAR / 72 определения модов. Снимок: `isg-materials.mresnapshot`, ранее полученный из Minecraft; сборка использована только для чтения.

| Показатель | Результат |
|---|---:|
| Зарегистрированные runtime Items | 29 442 |
| Зарегистрированные runtime Blocks | 11 286 |
| Runtime BlockItems (явный flag) | 10 322 |
| BlockItem с неизвестным связанным Block ID в старом snapshot | 10 322 |
| Подтверждённые блоки без BlockItem | 0 подтверждений; фактическое число UNKNOWN |
| Статические кандидаты Block до runtime merge | 7 246 |
| Определения assets всех видов | 50 696 |
| Model-only определения без соответствующего Item/Block в объединённом индексе | 10 361 |
| Определения `models/block`, не превращённые в Block ID | 7 827 |
| Успешно отрендеренные 3D-превью | Не измеряется: 3D отложен |

Model-only и исключённые модели считаются как определения с физическим источником, а не уникальные зарегистрированные ресурсы. Несколько определений одного model ID сохраняются. В живом объединённом каталоге дополнительно доступны 31 статический Item и 106 статических Block, отсутствующих в данном runtime snapshot; UI показывает их отдельно от runtime-confirmed. Метрики сохранены в [PHASE17_METRICS.json](PHASE17_METRICS.json).

### Повторение проверки

```powershell
Set-Location 'L:\My Add\RecipeEditor\.manual-tests\worktrees\phase17'
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat :registry:performanceTest --tests '*UnifiedResourcePerformanceTest'
.\gradlew.bat :app:externalTest --tests '*UnifiedResourceAuditTest' --tests '*VanillaResourceLoadingTest' '-PrealModpack=G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh' '-PresourceAuditSnapshot=L:\My Add\RecipeEditor\.manual-tests\resources\isg-materials.mresnapshot'
.\gradlew.bat :app:run
```

Локальные журналы находятся в `build/phase17-verification/` в рабочем дереве. Gradle XML/HTML — в `*/build/test-results` и `*/build/reports/tests`. Они не добавлены в Git; `clean` удаляет build artifacts.

## Коммиты и файлы

```text
8ac1644 docs: organize development reports
080f48d fix(registry): separate registered resources from model definitions
52822c6 feat(registry): introduce unified item and block catalog
515109c feat(ui): integrate block items into item browser
70290c8 refactor(ui): remove redundant blocks navigation
b3bc9e1 test(resources): add block model and registry regression tests
```

Сам отчёт, метрики и индекс отчётов добавлены заключительным документационным коммитом `docs: record phase 17 registry and browser verification`; его SHA доступен через `git log -1`.

Файлы реализации и тестов Phase 17 (перемещение прежних отчётов отдельно отражено в `8ac1644`):

- `app/src/main/java/dev/recipeeditor/app/DefaultStaticAnalysisService.java`
- `app/src/test/java/dev/recipeeditor/app/StaticAnalysisServiceTest.java`
- `app/src/test/java/dev/recipeeditor/app/UnifiedResourceAuditTest.java`
- `companion-client/src/main/java/dev/recipeeditor/companionclient/RegistryMergeService.java`
- `companion-client/src/test/java/dev/recipeeditor/companionclient/RegistryMergeServiceTest.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/MainWindow.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/NavigationSection.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceBrowserViewModel.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceCardView.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/NavigationSectionTest.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/ResourceBrowserViewModelTest.java`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/UnifiedResourceBrowserTest.java`
- `docs/reports/PHASE17_METRICS.json`
- `docs/reports/PHASE17_REGISTRY_AUDIT.md`
- `docs/reports/PHASE17_REPORT.md`
- `docs/reports/README.md`
- `minecraft-companions/forge-1.20.1/src/main/java/dev/recipeeditor/forge/BlockRegistryCollector.java`
- `minecraft-companions/forge-1.20.1/src/main/java/dev/recipeeditor/forge/ItemRegistryCollector.java`
- `minecraft-companions/forge-1.20.1/src/main/java/dev/recipeeditor/forge/RuntimeRegistryScanner.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeResourceReferences.java`
- `registry/src/main/java/dev/recipeeditor/registry/BlockItemRelationships.java`
- `registry/src/main/java/dev/recipeeditor/registry/FluidResourceIndexer.java`
- `registry/src/main/java/dev/recipeeditor/registry/ItemResourceIndexer.java`
- `registry/src/main/java/dev/recipeeditor/registry/RegistryIndex.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceAssetCatalog.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceAssetRecord.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceEntryBuilder.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceIdentity.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceReference.java`
- `registry/src/main/java/dev/recipeeditor/registry/UnifiedResourceIndex.java`
- `registry/src/test/java/dev/recipeeditor/registry/BlockItemRelationshipTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/BlockRegistryNormalizationTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/ResourceDiagnosticsTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/ResourceIndexersTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/UnifiedResourceIndexTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/UnifiedResourcePerformanceTest.java`

## Git Push

Проверенные коммиты отправлены в `origin/feat/phase-17-unified-resources-3d-block-rendering`. Ветка отслеживает одноимённую remote branch. Финальная публикация документационного коммита проверяется сравнением локального HEAD и remote HEAD. Pull request автоматически не создавался.


## Ограничения

Сохранённый настоящий snapshot ISG не содержит `blockId` и `hasBlockItem`. Поэтому фактические связи его BlockItem и количество блоков без Item неизвестны. Ноль подтверждённых блоков без Item не означает, что таких блоков нет. Все блоки доступны через технический фильтр. Новый Companion собран отдельно; live Minecraft Client с новым сборщиком в этом этапе не запускался. Поддержка новых метаданных проверяется воспроизводимым snapshot round trip.

Статическое сканирование не может доказать регистрацию программно создаваемых ресурсов. Отсутствующие связи и неподдерживаемые изображения остаются диагностируемыми. Число успешно отрендеренных 3D-превью не заявляется: 3D-рендеринг отложен по заданию.

Merge, Git Tag и GitHub Release не выполняются. Следующий этап требует отдельной команды.
