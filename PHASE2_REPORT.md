# Phase 2 — отчёт

Реализован импорт локальных Minecraft-инстансов, без перехода к Phase 3.

## Проверки

- ` .\gradlew.bat clean build` — BUILD SUCCESSFUL.
- ` .\gradlew.bat test` — BUILD SUCCESSFUL (повторно UP-TO-DATE после clean build).
- Отдельный прогон `:modpack-parser:test -PrealModpack=...` — BUILD SUCCESSFUL.
- Суммарно в последнем прогоне: **47 тестовых случаев, 46 выполнено успешно, 1 пропущено, 0 failures, 0 errors**. Пропущен тест создания symlink: Windows не разрешает создание символической ссылки в текущем окружении. Прямая проверка пути за корнем выполнена. В обычном clean build дополнительно пропускается opt-in тест реального инстанса.
- Реальная сборка: `G:\PrismLauncher\instances\Cosmic Frontiers`, Minecraft **1.20.1**, Forge **47.4.0**, **267 JAR**, **285 определений модов**, **0 JAR с ERROR**, **897 диагностических сообщений** (в том числе непроверенные ограничения версий, дублирующие multi-loader объявления и неподдерживаемые сведения). Определения не равны числу уникальных модов.
- На реальной сборке обнаружен нестандартный `[[dependencies]]` у arseng. Добавлен регрессионный тест: сведения о моде сохраняются, неподдерживаемая зависимость явно диагностируется.
- JavaFX запускался через `:app:run`. Визуально проверены каталог, метаданные и зависимости реального инстанса `ISG-0.6.0-alpha.1-harsh`: Minecraft 1.20.1, Forge 47.4.28, 70 JAR / 71 запись. Автоматические клики при пользовательском вводе не продолжались; отдельный полный автоматизированный UI-тест поиска/фильтров не проводился.
- Последняя доработка CSS улучшает контраст полей и строк таблицы. Предупреждение JavaFX о classpath осталось от Phase 1; механизм запуска сохранён.

## Архитектура и ограничения

Десять модулей сохранены, внутренние зависимости не образуют циклов. `desktop-ui -> core`, `modpack-parser -> core`, app связывает реализацию и UI через контракт. Модели и сервисный контракт находятся в `core.importing`; детекторы, нормализация и ограниченное чтение архивов — в `modpack-parser`; фоновые операции не выполняются в JavaFX Thread.

Форматы: Forge/NeoForge TOML через org.tomlj, Fabric/Quilt JSON через Jackson. Присутствуют обнаружение Vanilla/Forge/NeoForge/Fabric/Quilt/UNKNOWN, диагностика конфликтов и сохранение источников. Детальный список классов, их обязанностей, форматов и лимитов находится в README.

Ограничения: версия зависимостей не вычисляется; вложенные JAR и provides aliases не разрешаются; старый mcmod.info не поддерживается; произвольные профили лаунчеров и рекурсивное обнаружение инстансов отсутствуют. Неизвестные сведения не подменяются вымышленными. Статус таблицы описывает чтение JAR; сообщения зависимости видны отдельно. Импорт в памяти, сохранения проектов пока нет. Исходные сборки не изменялись, сторонние JAR в проект не копировались.

## Изменённые файлы

- `modpack-parser/build.gradle.kts`
- `app/src/main/java/dev/recipeeditor/app/RecipeEditorApplication.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/MainWindow.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/NavigationSection.java`
- `desktop-ui/src/main/resources/dev/recipeeditor/ui/theme.css`
- `README.md`

## Новые файлы

- `core/src/main/java/dev/recipeeditor/core/importing/DependencyKind.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ImportDiagnostic.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ImportOperation.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ImportProgress.java`
- `core/src/main/java/dev/recipeeditor/core/importing/MinecraftVersion.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModDefinition.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModDependency.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModLoaderDefinition.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModLoaderType.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModMetadata.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModScanResult.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModpackDefinition.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModpackImportResult.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ModpackImportService.java`
- `core/src/main/java/dev/recipeeditor/core/importing/ScanStatus.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/ModCatalogView.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/DefaultModpackImportService.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/DependencyValidator.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/DetectionResult.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/FabricMetadataParser.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ForgeMetadataParser.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ImportLimits.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/InstanceMetadataReader.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/JsonMetadataReader.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/MetadataContext.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/MetadataEvidence.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/MinecraftVersionDetector.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ModLoaderDetector.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ModMetadataParseResult.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ModMetadataParser.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ModScanner.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ModpackDetector.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/NeoForgeMetadataParser.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/ParserSupport.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/QuiltMetadataParser.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/SafeFiles.java`
- `modpack-parser/src/main/java/dev/recipeeditor/modpackparser/TomlMetadataReader.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/DependencyValidatorTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/MetadataParserTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/MinecraftVersionDetectorTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/ModLoaderDetectorTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/ModScannerTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/ModpackDetectorTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/ModpackImportServiceTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/RealModpackImportTest.java`
- `modpack-parser/src/test/java/dev/recipeeditor/modpackparser/TestFixtures.java`
- `PHASE2_REPORT.md`
