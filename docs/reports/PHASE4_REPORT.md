# Phase 4 — Recipe Editing System

Реализован слой пользовательского редактирования над исходным каталогом Minecraft 1.20.1. Основная проверенная среда — Forge. Phase 5 не начата.

## Проверки

- Перед изменениями: существующий ` .\gradlew.bat test` — BUILD SUCCESSFUL.
- После реализации: ` .\gradlew.bat clean build` и отдельный ` .\gradlew.bat test` — BUILD SUCCESSFUL.
- В обычном прогоне **133 тестовых случая: 129 успешно, 4 пропущены, 0 failures/errors**. Три opt-in проверки реальных инстансов и один Windows symlink test (нет права создания ссылки) пропускаются по явным условиям.
- `:app:test --tests '*RealRecipeEditingTest' -PrealModpack=...` — BUILD SUCCESSFUL. Реальный рецепт **ae2netanalyser:analyser** из `AE2NetworkAnalyzer-1.20-1.0.6-forge.jar` сборки Cosmic Frontiers: UPDATE, Undo/Redo, duplicate, CREATE→DELETE, восстановление проекта, source conflict проверены. Исходный JAR имеет тот же SHA-256. Проект записывался только во временный каталог JUnit, сторонние моды в репозиторий не копировались.
- Внутренний граф Gradle: 10 модулей, циклов нет.

## GUI

Приложение запускалось через ` .\gradlew.bat :app:run`. В реальном инстансе ISG-0.6.0-alpha.1-harsh наблюдались открытая форма создания shaped, сетка, picker, ошибки обязательных полей и отключённый Save для невалидного черновика. Обнаружена проблема вложенных модальных окон; владельцы picker исправлены на окно редактора, лишнее вложенное подтверждение заменено сбором альтернатив в picker. После перезапуска наблюдался отзывчивый каталог с логическим DELETE и доступным Undo. Добавлены заметные префиксы статусов и горячие клавиши вне текстовых полей.

**Полный ручной проход всех операций через GUI, включая сохранение и повторное открытие project.json, не заявляется как выполненный.** Эти сценарии проверены unit/integration тестами, в том числе на реальном рецепте. Автоматические клики ограничивались обнаружением пользовательского ввода в окне. Тестовые изменения GUI отброшены с явного разрешения пользователя перед чистой сборкой.

## Новые классы и решения

- `RecipeEditorService`: per-project контекст, создание/update/delete/duplicate/reset, эффективное состояние и источник конфликтов. Исходные RecipeDefinition остаются immutable. UPDATE для конфликтующего ID хранит references всех кандидатов и создаёт один override, не уничтожая оригиналы.
- `RecipeChange`, `RecipeChangeSet`, статусы: компактный overlay CREATE/UPDATE/DELETE; CREATE→DELETE сворачивается, reset восстанавливает исходные данные. Timestamp и base SHA-256 сохраняются. Снимки immutable.
- `EffectiveRecipeIndex`: отдельные инкрементальные delta lookup по входам/выходам/type/tag. Один edit не копирует/не перестраивает импортированный индекс. UI обновляет строки затронутого ID, не создавая каталог заново.
- `RecipeCommand` / `CommandHistory`: execute/undo, ограниченная история, очистка Redo после нового действия, откат неуспешного execute/redo/undo с сохранением позиции истории.
- `RecipeValidator`: повторно использует version-specific RecipeParser из Phase 3, разделяет errors и warnings. Отсутствие runtime registration не блокирует save. Некорректные ID/JSON/обязательные поля/числа блокируют сохранение.
- `RecipeEditSession`: самостоятельный черновик и режим, revision и dirty-state. `RecipeDraftPreviewService` готовит актуальную visual/validation/diff модель в worker; invalid raw не маскируется старым visual состоянием.
- `RecipeFormService`: tree patches, сохранность неизвестных полей, shaped trimming/key generation, shapeless и output patches. Нет JSON-парсинга в UI-контроллерах.
- `RecipeDiffService`: структурное сравнение JSON, включая неизвестные поля, массивы и числовые параметры; пробелы/порядок ключей не считаются изменением.
- `ProjectDocument`, `StoredRecipeChange`, `ProjectPersistence`: версионированный контракт, не зависящий от JavaFX или реализации файловой системы.
- `app.project.FileProjectPersistence`: async load/save, integrity hash, bounded serialization, временный файл, flush, atomic replace/fallback, .bak предыдущего корректного файла. Запись внутрь source modpack запрещена. `ProjectFormatMigration` позволяет явно регистрировать миграции к текущему формату; по умолчанию только format 1.
- `SourceConflict`: изменённые/исчезнувшие/новые конфликтующие исходники помещают сохранённый change в карантин. Он сохраняется при следующем Save и доступен для review/copy/explicit discard; автоматический rebase отсутствует.
- `IngredientPicker`, `RecipeOutputEditor`, `RecipeEditorDialog`, `EditorTasks`: переиспользуемые формы, известные теги, альтернативы, количество результата, grid/list/roles, Visual/Advanced/Raw/Diff и отдельный worker. Generic fluid-reference picker подготовлен для будущих адаптеров и не создаёт фиктивный Vanilla fluid input.
- `MainWindow`/`RecipeBrowserView`: Project Open/Save, изменение каталога и context menu, подтверждения удаления/закрытия/смены проекта, изменение статусов, отдельная текстовая и проектная история.

Отдельного модуля проектов в Phase 1–3 не было; файловое управление проектом размещено в `app.project`, а contract — в recipe-engine. Все десять Gradle-модулей сохранены, новых циклов нет.

## Поддержка редактирования

Специализированные формы: shaped, shapeless, smelting, blasting, smoking, campfire cooking, stonecutting, smithing transform и smithing trim. Учитываются разные result structures, pattern/key, output count, cookingtime/experience и template/base/addition; trim output динамический. Число Vanilla outputs ограничено одним. При явных raw-изменениях выполняется повторный разбор и актуализируется visual preview.

**Unknown recipes имеют только ограниченный Raw JSON Editor: корректность JSON/ID/type, сохранение raw, diff и history. Это не полноценная семантическая поддержка неизвестного рецепта.** Custom adapters и их свойства не угадываются.

## Ограничения / пока не реализовано

- Экспорт в Minecraft, применение проекта к реальной конфигурации игры (Phase 5).
- Полные адаптеры GregTech/Create/Mekanism, EU/t/duration, семантические fluid inputs/outputs, probability и custom conditions. Generic fluid picker существует как extension point.
- Runtime-подтверждение registry, выполнение скриптов/Java-кода, активный datapack load order.
- Drag-and-drop grid (есть click/replace/clear), полный рендер Minecraft.
- Сохранение Undo stack между перезапусками; сохранение промежуточной несохранённой формы в project.json. Сохраняется только применённый ChangeSet.
- Автоматический rebase/merge источников и поиск перемещённого инстанса.
- Готовые миграции прежних форматов (раньше формата проекта не существовало); hook проверен тестом с явно зарегистрированным переходом.
- Полная ручная GUI-регрессия всех операций.

Лимиты: 2 MiB UTF-8 на recipe JSON, 48 MiB / 10000 changes на проект; история до 200 команд / оценки 32 MiB JSON. Исходные JAR не меняются. Unknown fields сохраняются при изменении поддержанных полей; явное переписывание Raw JSON — осознанная правка пользователя. Безопасность путей не является ОС-песочницей против параллельной подмены файлов другим процессом.

## Использование

Import Modpack → отдельный инстанс 1.20.1 → Recipes → Create/Edit/Duplicate → заполнить Visual/Advanced/Raw → Save to project → Close → Save Project вне исходной сборки. Open Project повторно импортирует источник и восстанавливает effective recipes. Source conflicts показывает заблокированные изменения. Delete требует подтверждения, Reset/Undo восстанавливают данные. Save to project в форме — изменение в памяти; Save Project — запись файла; экспорт отсутствует.

Подробная инструкция, сочетания клавиш и восстановление backup — в README.

## Изменённые файлы

- `app/build.gradle.kts`
- `app/src/main/java/dev/recipeeditor/app/RecipeEditorApplication.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeIndex.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/MainWindow.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeBrowserView.java`
- `desktop-ui/src/main/resources/dev/recipeeditor/ui/theme.css`
- `README.md`

## Новые файлы

- `app/src/main/java/dev/recipeeditor/app/project/FileProjectPersistence.java`
- `app/src/main/java/dev/recipeeditor/app/project/ProjectFormatMigration.java`
- `app/src/test/java/dev/recipeeditor/app/RealRecipeEditingTest.java`
- `app/src/test/java/dev/recipeeditor/app/project/ProjectPersistenceTest.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/EditorTasks.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/IngredientPicker.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeEditorDialog.java`
- `desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeOutputEditor.java`
- `integrations/src/test/java/dev/recipeeditor/integrations/editor/EditorFixtures.java`
- `integrations/src/test/java/dev/recipeeditor/integrations/editor/RecipeChangeSetTest.java`
- `integrations/src/test/java/dev/recipeeditor/integrations/editor/RecipeEditorServiceTest.java`
- `integrations/src/test/java/dev/recipeeditor/integrations/editor/RecipeFormServiceTest.java`
- `integrations/src/test/java/dev/recipeeditor/integrations/editor/RecipeValidatorTest.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/CommandHistory.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/EffectiveRecipeIndex.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/OriginalRecipeReference.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/ProjectDocument.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/ProjectPersistence.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeChange.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeChangeSet.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeChangeType.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeCommand.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeDiffService.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeDraftPreviewService.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeEditPreview.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeEditSession.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeEditorService.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeFormService.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeModificationStatus.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeValidationResult.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/RecipeValidator.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/SourceConflict.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/StoredRecipeChange.java`
- `recipe-engine/src/main/java/dev/recipeeditor/recipeengine/editor/ValidationIssue.java`
- `recipe-engine/src/test/java/dev/recipeeditor/recipeengine/editor/CommandHistoryTest.java`
- `recipe-engine/src/test/java/dev/recipeeditor/recipeengine/editor/RecipeDiffServiceTest.java`
- `recipe-engine/src/test/java/dev/recipeeditor/recipeengine/editor/RecipeEditSessionTest.java`
- `PHASE4_REPORT.md`
