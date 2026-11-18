# Phase 3 — Recipe Discovery & Registry Indexing

Реализована система статического обнаружения ресурсов и JSON-рецептов для Minecraft 1.20.1, с проверкой реальных Forge-инстансов. Phase 4 не начата.

## Результаты проверки

- ` .\gradlew.bat clean build` — BUILD SUCCESSFUL.
- ` .\gradlew.bat test` — BUILD SUCCESSFUL, повторная проверка после сборки UP-TO-DATE.
- Последний ` .\gradlew.bat build` — BUILD SUCCESSFUL после уточнения ZIP-datapack fixture и комментариев пакетов.
- Обычный прогон: **104 тестовых случая, 101 успешно, 3 пропущены, 0 failures, 0 errors**. Пропущены два opt-in real-modpack теста и создание symlink (Windows не разрешает эту операцию). Прямая проверка выхода за корень выполнена.
- `:app:test -PrealModpack=...` — BUILD SUCCESSFUL, реальный тест выполнен отдельно.
- Сохранённый результат последнего полного анализа Cosmic Frontiers:

```text
JARs=267; items=19537; blocks=23888; fluids=37; recipes=16692; status={INVALID=11, UNSUPPORTED=5139, PARTIALLY_PARSED=1124, PARSED=10418}; conflicts=266; diagnostics=7024; ms=14264
```

Сборка: `G:\PrismLauncher\instances\Cosmic Frontiers`, Minecraft 1.20.1 / Forge 47.4.0. Сверены размеры и время изменения исходных 267 JAR до/после анализа; изменений нет. Сторонние моды в репозиторий не копировались. Время анализа является измерением этого запуска, не гарантией производительности.

11 INVALID-кандидатов — пустые и нетипизированные файлы в recipes, включая служебный `_factories.json`, а не 11 доказанных ошибок работающей сборки. Наличие таких файлов может быть преднамеренным. UTF-8 BOM в реальных файлах ironfurnaces обработан с сохранением raw текста и покрыт тестом. Unknown serializers не считаются некорректными: они находятся в UNSUPPORTED.

## UI-проверка

JavaFX запускался через ` .\gradlew.bat :app:run`. В открытом приложении наблюдалась работа с реальным `ISG-0.6.0-alpha.1-harsh`: 70 JAR, 25692 ресурса, 11489 рецептов; первое индексирование около 7.4 секунды, повторное около 4 секунд. Видны фоновые этапы, каталог блоков с texture samples / placeholders, shaped viewer, поиск `calorite_block` с фильтром мода `ad_astra`.

Инструмент автоматических кликов останавливал действия при пользовательском вводе. Поэтому полный проход Raw JSON / Source / Recipes / Uses / Fluids через UI **не заявляется как проверенный вручную**. Данные источника, raw JSON, поиск uses/producing, динамические outputs, фильтрация типов и остальные основные правила покрыты unit-тестами. Доработки сброса выбранной записи, нормализации типа и layout grid проверены сборкой; полный автоматизированный UI-регрессионный набор не реализован.

## Архитектурные решения и классы

Сохранены десять модулей; граф внутренних Gradle-зависимостей проверен, циклов нет.

- `core`: типобезопасный `ResourceLocation`, общая безопасная инфраструктура `core.io.SafeFiles` из Phase 2. Модели импорта повторно используются без дублирования.
- `registry`: `ResourceSource`, `RegistryEntry`, Item/Block/FluidDefinition и категории достоверности; `ResourceScanner` выполняет ограниченное чтение ресурсов JAR и явных локальных источников. `ResourceLayout` задаёт пути поддерживаемой версии. `ResourceJsonReader` ограничивает глубину JSON и принимает BOM. `LocalizationService` обслуживает en_us/ru_ru и fallback. Индексаторы ресурсов и `RegistryIndex`/`RegistrySearchService` обслуживают каталог. `TagIndex` строит ограниченное итеративное раскрытие с циклами и сохраняет определения; `ModelTextureResolver` разрешает только простые preview references. `TextureResourceService` валидирует PNG и хранит ограниченный LRU, с отдельным ограниченным worker.
- `recipe-engine`: sealed ingredients (item/tag/alternatives/unknown), fixed/dynamic outputs, модель рецепта, layout, properties, источник, статусы и raw JSON. `RecipeParser` — расширение, `StaticRecipeScanner` сохраняет unsupported/invalid кандидаты. `RecipeIndex` строит lookup по ID/type/namespace/mod/input/output/tag, сохраняет конфликты; `RecipeSearchService` фильтрует результаты. Поиск входов/выходов не перебирает все рецепты при каждом обращении.
- `integrations`: `Minecraft1201Integration` содержит версионную возможность и набор парсеров. Пять семейств парсеров покрывают девять serializers; общая обработка ограничена проверкой JSON, ингредиентов, результатов, статусов и неизвестных полей.
- `app`: `DefaultStaticAnalysisService` связывает сканер, registry и recipe pipeline, управляет worker/future/cancellation и публикует только завершённый результат. `RecipeEditorApplication` создаёт и освобождает сервисы. После импорта автоматически начинается индексирование.
- `desktop-ui`: `OperationProgressView`, постраничный `ResourceBrowserView`, виртуализированный `RecipeBrowserView` и существующая оболочка. В UI нет чтения JAR, JSON-парсинга или построения доменных индексов.

Основные связи: `desktop-ui -> recipe-engine -> registry -> core`; `integrations -> recipe-engine`; `modpack-parser -> core`; app — точка композиции. Остальные модули Phase 1 сохранены.

## Поддерживаемые рецепты

crafting_shaped, crafting_shapeless, smelting, blasting, smoking, campfire_cooking, stonecutting, smithing_transform, smithing_trim. Поддерживаются item/tag/alternatives, количества, pattern/key, cookingtime/experience, роли template/base/addition. Smithing trim не имеет вымышленного fixed output. Дополнительные и неизвестные поля сохраняются в raw JSON; неподдержанные условия и semantics помечаются PARTIALLY_PARSED. Версии, отличные от 1.20.1, получают явное сообщение отсутствия поддержки.

## Использование

1. Запустите ` .\gradlew.bat :app:run` с JDK 21.
2. Нажмите Import Modpack и выберите отдельный инстанс, а не общий instances.
3. Дождитесь импорта и автоматического анализа либо отмените операцию. Index resources повторяет анализ по запросу.
4. Откройте Items / Blocks / Fluids, используйте поиск и Mod-фильтр. Resource language меняет язык ресурсов отдельно от UI.
5. Выберите item и Recipes/Uses либо откройте Recipes напрямую. Вкладки Raw JSON / Source показывают исходные данные и происхождение.
6. Верхняя Diagnostics содержит сообщения импорта и анализа.

Полные лимиты, форматы локальных источников и инструкции запуска находятся в README.

## Ограничения и нереализованные функции

Статическая модель не подтверждает runtime-регистрацию. Модели блоков могут быть техническими частями геометрии; блоки не создают item автоматически. Жидкости ограничены explicit fluid-tag references, localizations/textures не создают вымышленные IDs. При отсутствии ресурса нельзя утверждать, что его нет в игре.

Не вычисляются активные datapacks, load order, replace, Forge conditions и сложные custom ingredients. Поиск тегов даёт статически возможные совпадения. Конфликтующие определения сохраняются, победитель не выбирается. Неизвестные рецепты доступны только как raw/source/status.

Пока отсутствуют runtime Companion и фактические registries, выполнение/анализ скриптов, Java-generated recipes, специальные GregTech/Create/Mekanism adapters, редактирование, полный Minecraft renderer/atlas, persistent cache, сканирование общих vanilla JAR/resourcepacks за пределами выбранного инстанса и сохранение проектов. Vanilla base-game данные могут отсутствовать, если их не содержат просканированные источники. Результаты в памяти повторно используются при навигации; явное повторное индексирование перечитывает файлы для актуальности.

## Изменённые файлы

- `README.md`
- `app/build.gradle.kts`
- `app/src/main/java/dev/recipeeditor/app/RecipeEditorApplication.java`
- `desktop-ui/build.gradle.kts`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/MainWindow.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/NavigationSection.java`
- `desktop-ui/src/main/resources/dev/recipeeditor/ui/theme.css`
- `desktop-ui/src/test/java/dev/recipeeditor/ui/NavigationSectionTest.java`
- `integrations/build.gradle.kts`
- `integrations/src/main/java/dev/recipeeditor/integrations/package-info.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/DefaultModpackImportService.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/DependencyValidator.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/InstanceMetadataReader.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/JsonMetadataReader.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ModScanner.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ModpackDetector.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/TomlMetadataReader.java`
- `recipe-engine/build.gradle.kts`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/package-info.java`
- `registry/build.gradle.kts`
- `registry/src/main/java/dev/recipeeditor/registry/package-info.java`

## Новые файлы

- `app/src/main/java/dev/recipeeditor/app/DefaultStaticAnalysisService.java`
- `app/src/test/java/dev/recipeeditor/app/RealStaticAnalysisTest.java`
- `app/src/test/java/dev/recipeeditor/app/StaticAnalysisServiceTest.java`
- `core/src/main/java/dev/recipeeditor/core/ResourceLocation.java`
- `core/src/test/java/dev/recipeeditor/core/ResourceLocationTest.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/OperationProgressView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeBrowserView.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ResourceBrowserView.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/AbstractVanillaRecipeParser.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/CookingRecipeParser.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/Minecraft1201Integration.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/ShapedRecipeParser.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/ShapelessRecipeParser.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/SmithingRecipeParser.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/StonecuttingRecipeParser.java`
- `integrations/src/main/java/dev/recipeeditor/integrations/Vanilla1201Parsers.java`
- `integrations/src/test/java/dev/recipeeditor/integrations/VanillaRecipeParserTest.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/DiscoveryOperation.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/DiscoveryResult.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/IngredientDefinition.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeDefinition.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeIndex.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeLayout.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeOutput.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeParseContext.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeParseResult.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeParseStatus.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeParser.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeProperty.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeSearchService.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeSource.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeTypeDefinition.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/StaticAnalysisService.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/StaticRecipeScanner.java`
- `recipe-engine/src/test/java/dev/recipeeditor/recipeengine/RecipeFixtures.java`
- `recipe-engine/src/test/java/dev/recipeeditor/recipeengine/RecipeIndexTest.java`
- `recipe-engine/src/test/java/dev/recipeeditor/recipeengine/RecipeSourceTest.java`
- `registry/src/main/java/dev/recipeeditor/registry/BlockDefinition.java`
- `registry/src/main/java/dev/recipeeditor/registry/BlockResourceIndexer.java`
- `registry/src/main/java/dev/recipeeditor/registry/DiscoveryConfidence.java`
- `registry/src/main/java/dev/recipeeditor/registry/DiscoveryLimits.java`
- `registry/src/main/java/dev/recipeeditor/registry/FluidDefinition.java`
- `registry/src/main/java/dev/recipeeditor/registry/FluidResourceIndexer.java`
- `registry/src/main/java/dev/recipeeditor/registry/ItemDefinition.java`
- `registry/src/main/java/dev/recipeeditor/registry/ItemResourceIndexer.java`
- `registry/src/main/java/dev/recipeeditor/registry/LocalizationService.java`
- `registry/src/main/java/dev/recipeeditor/registry/ModelTextureResolver.java`
- `registry/src/main/java/dev/recipeeditor/registry/RegistryEntry.java`
- `registry/src/main/java/dev/recipeeditor/registry/RegistryIndex.java`
- `registry/src/main/java/dev/recipeeditor/registry/RegistrySearchService.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceDocument.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceEntryBuilder.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceJsonReader.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceKind.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceLayout.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceScanResult.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceScanner.java`
- `registry/src/main/java/dev/recipeeditor/registry/ResourceSource.java`
- `registry/src/main/java/dev/recipeeditor/registry/SourceType.java`
- `registry/src/main/java/dev/recipeeditor/registry/TagDefinition.java`
- `registry/src/main/java/dev/recipeeditor/registry/TagIndex.java`
- `registry/src/main/java/dev/recipeeditor/registry/TextureResourceService.java`
- `registry/src/test/java/dev/recipeeditor/registry/LocalizationServiceTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/ResourceFixtures.java`
- `registry/src/test/java/dev/recipeeditor/registry/ResourceIndexersTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/ResourceScannerTest.java`
- `registry/src/test/java/dev/recipeeditor/registry/TextureResourceServiceTest.java`
- `PHASE3_REPORT.md`

## Перемещённый файл

`modpack-parser/src/main/java/dev/recipeeditor/modpackparser/SafeFiles.java` перенесён в `core/src/main/java/dev/recipeeditor/core/io/SafeFiles.java`; все пользователи Phase 2 обновлены.
