# Phase 16 — результат шагов 1–4

## Объём и Git

Выполнены только шаги, разрешённые заключительной частью задания: Git Workflow,
Architecture Audit, Universal Adapter Framework, Botania Support. Следующие шаги
Phase 16 требуют отдельной команды.

- Ветка: `feat/phase-16-modded-recipe-support`.
- База: `feat/phase-15-recipe-editor-ingredient-ux`, коммит `012173a`.
- На начало работы `origin/master` указывал на `63627c3`; Phase 15 ещё не была
  объединена. Незапушенных коммитов Phase 15 не было; ветка Phase 16 отсутствовала.
- Пользовательские изменения `.manual-tests/e2e-isg/gui-project.json` и `.bak`,
  старые логи и локальные артефакты сохранены и не включаются в коммиты задачи.
- Версия приложения остаётся `1.1.0-alpha.1`; Companion `0.1.0`, protocol `1`,
  project format `1` не менялись.
- Merge, tag, release и публикация пакетов не выполняются.

## Причина исходной ошибки

`DefaultStaticAnalysisService` получает парсеры из `Minecraft1201Integration` /
`ProjectRecipeParsers`. До этой задачи там регистрировались только Vanilla и
существующий GregTech parser. Для `botania:brew` отсутствовал обработчик, поэтому
`StaticRecipeScanner` корректно оставлял исходный JSON со статусом `UNSUPPORTED`.
Без нормализованного результата Viewer показывал `Unknown result`.

В старом открытом приложении это состояние было фактически наблюдено на
`botania:brew/allure`. Перед закрытием проверено: 0 изменений, проект совпадает
с сохранённым snapshot. Исходные JAR и файлы сборки не изменялись.

## Архитектура

- `ModdedRecipeAdapter` расширяет существующий `RecipeParser`, не дублируя
  scanner и validator. Parsing, JavaFX rendering и export writing разделены.
- `ModdedRecipeAdapterRegistry` разрешает точные serializer IDs. Дублирующиеся
  ключи отклоняются сразу; наличие одного адаптера не включает весь namespace.
- `RecipeTypeCapabilities` независимо описывает просмотр, создание,
  редактирование, экспорт и потребность в runtime/integration.
- `RecipeMachineLayout` — JavaFX-free DTO с переменными группами и отдельными
  каналами ITEM/BLOCK/FLUID/CHEMICAL. Последние два не означают реализации
  адаптеров Mekanism или GT8: это точки расширения общего каркаса.
- Для модовых рецептов metadata layout формируется при фоновом разборе и
  сохраняется в `RecipeLayout`. Выбор строки не перечитывает исходный JSON.
  JavaFX Nodes создаются только для выбранного рецепта; таблица виртуализирована.
- `ProcessingRecipeLayoutView` использует общие слоты, превью, tooltips и стрелку.
  LINEAR/BATCH/BREWING задают расположение; фактическое число ингредиентов не
  дополняется вымышленными слотами 3x3.
- `BrewDefinition` / `RecipeOutput.Brew` — ссылка на brew definition, отдельная
  от ItemStack. Brew не индексируется как предмет; Analyzer сохраняет opaque
  семантику вместо ложных детерминированных зависимостей.
- `RecipeSemanticLocalization` использует уже прочитанный `LocalizationService`
  на indexing worker. Ключ названия берётся из объявленного brew ID по проверенному
  формату API, а не из Recipe ID. Неизвестный ключ оставляет полный brew ID.
- Исходный JSON, неизвестные поля и provenance сохраняются. Непонятные расширения
  дают `PARTIALLY_PARSED`; повреждённые обязательные поля — `INVALID`.
- Не менялись Companion protocol, runtime snapshot schema и алгоритм merge.
  Runtime normalized state не становится исходным редактируемым JSON.

Подробный начальный аудит: [PHASE16_ARCHITECTURE_AUDIT.md](PHASE16_ARCHITECTURE_AUDIT.md).

## Проверенный Botania

Использован установленный `Botania-1.20.1-456-FORGE.jar` из ISG.
SHA256: `3cf540357c9cb5e460266b17a02617656df6cc7090a565fca2a462b1c3aba4ae`.
Проверены Manifest, исходные JSON и байткод serializer-классов через `javap`:
BotanicalBreweryRecipe.Serializer, PetalsRecipe.Serializer,
ManaInfusionRecipe.Serializer, StateIngredientHelper и Brew.getTranslationKey.

| Mod | Recipe Type | Parse | View | Edit | Create | Export |
|---|---|---|---|---|---|---|
| Botania 456 | `botania:brew` (20 определений) | PARSED | Да, brew reference | Нет | Нет | Нет |
| Botania 456 | `botania:petal_apothecary` (43) | PARSED | Да, ingredients/reagent/result | Нет | Нет | Нет |
| Botania 456 | `botania:mana_infusion` (139) | PARSED | Да, input/output/mana/catalyst | Нет | Нет | Нет |
| Botania | остальные serializers | Без расширения | Raw/Source/Diagnostics | Не заявляется | Не заявляется | Без расширения |
| GregTech | machine recipes | Шаг 5 отложен | Существующая поддержка не расширялась | Без изменений | Без изменений | Без изменений |
| Create | processing | Шаг 6 отложен | Без изменений | Без изменений | Без изменений | Без изменений |
| Mekanism | chemical/machine | Шаг 7 отложен | Без изменений | Без изменений | Без изменений | Без изменений |
| Ad Astra | machine recipes | Шаг 8 отложен | Без изменений | Без изменений | Без изменений | Без изменений |
| NuclearCraft | machine recipes | Шаг 8 отложен | Без изменений | Без изменений | Без изменений | Без изменений |

Просмотр трёх Botania serializers не объявляется полной поддержкой Botania.
Авторинг относится к последующему шагу 9, экспорт — к шагу 10. В текущем UI
заблокированы Edit/Duplicate/Delete и создание этих типов. Прямой вызов editor
также оставляет Raw JSON read only и Save disabled. Существующий
ExportCapabilityResolver/format exporters отклоняют Botania; KubeJS-методы
и неподтверждённые serializers не генерируются.

## UI и реальные данные

Использована сборка `G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh`:
29 096 static resources, 11 490 recipe definitions. Дополнительно загружен
настоящий сохранённый runtime snapshot из `.manual-tests/resources`; это не новая
live-сессия Minecraft.

Фактически проверены в JavaFX окне:

- Allure: `Brew: Marine Allure`, `Botania Brewing`, `PARSED`, четыре изображения
  ингредиентов, отдельный BREW RESULT и `Brew ID: botania:allure`; фиктивного
  ItemStack-результата нет.
- Agricarnation: шесть ингредиентов, отдельный reagent и реальный результат,
  tag badges и индексированные варианты в tooltips.
- Acacia Leaves mana infusion: выход 2, mana 2000, отдельный BLOCK catalyst
  `botania:conjuration_catalyst`. Катализатор не подменяется предметным ingredient.
- Возможности просмотра/авторинга видны отдельно; неподдерживаемые действия
  отключены. Raw JSON, Source и Diagnostics остаются доступны.

После первого просмотра описание capabilities перенесено под визуальный рецепт:
название и результат остаются выше служебного текста. Из пользовательского текста
убраны номера внутренних шагов разработки.

## Проверки

- `.\gradlew.bat clean build`: **BUILD SUCCESSFUL**, 3m 15s, 82 задачи выполнены.
- `.\gradlew.bat test`: **BUILD SUCCESSFUL**, 5s, 53 задачи up-to-date после чистой сборки.
- Unit: **325** тестов, **323 passed**, **2 skipped**, 0 failures/errors.
- Integration: **74 passed**, 0 skipped/failures/errors.
- Отдельный opt-in внешний прогон: **9 passed**, 0 skipped/failures/errors,
  BUILD SUCCESSFUL за 1m 33s. Включены все восемь Botania/framework проверок
  и существующая регрессия визуального редактирования Vanilla TNT.
- `git diff --check` / проверка staged diff: успешно.
- Production architecture check: без циклов и запрещённых UI-зависимостей engine.

Внешние проверки покрывают все 202 исходных определения; variable layout и размеры
40/64/80; семантический brew без ItemStack; сохранение неизвестных полей;
отклонение повреждённых обязательных полей; блокировку авторинга и экспорта;
Undo/Redo и project roundtrip в общем document pipeline; merge настоящего cached
runtime snapshot с сохранением статического brew и provenance. Это не live-тест
в Minecraft.

Локальные логи: `.manual-tests/phase16-build.log`, `phase16-test.log`,
`phase16-external-final.log`, `phase16-visual-final.log`. Они не включаются в Git.

## Коммиты реализации и Git Push

| SHA | Изменение |
|---|---|
| `12c7628` | `feat(recipes): introduce extensible modded recipe adapters` |
| `50c97b7` | `feat(botania): add specialized recipe parsing and layouts` |
| `7e80452` | `fix(ui): prioritize modded recipe results in viewer` |
| `beb583e` | `test(recipes): add modded recipe integration tests` |

Git Push реализации и тестов выполнен успешно: удалённая ветка
`origin/feat/phase-16-modded-recipe-support` создана, `beb583e` отправлен.
Этот итоговый отчёт добавляется отдельным документирующим коммитом; итоговый SHA
и результат его отправки сообщаются в финальном ответе.


Положительные Botania fixtures читаются непосредственно из установленного JAR.
Код, изображения, lang-файлы и JSON Botania не копируются в репозиторий и не
распространяются. Тестовые отрицательные случаи — явные мутации прочитанного JSON
или заведомо неполные входы; они не выдаются за реальные рецепты.
Исходный проект Botania: [Vazkii и участники](https://github.com/VazkiiMods/Botania).

## Ограничения

- Brew reference не доказывает runtime-регистрацию brew, эффекты, расход маны
  или конкретный контейнер результата. Эти значения не выдумываются.
- Поддержка проверена на структуре установленного артефакта 456; весь диапазон
  версий Botania не объявляется проверенным.
- Сложные/custom ингредиенты, state predicates и неизвестные условия сохраняются
  без заявления полной семантической поддержки; просмотр сопровождается partial
  status/diagnostics.
- Изображения используют существующий bounded flat renderer, а не полную
  Minecraft 3D geometry.
- Игра в рамках этой задачи не запускалась; live reload и установка экспорта
  в сборку не проверялись. Cached snapshot merge проверяется отдельно.

## Запуск

```powershell
.\gradlew.bat :app:run
```

Opt-in проверки реального Botania и Vanilla regression:

```powershell
.\gradlew.bat :app:externalTest `
  --tests '*Botania*Test' `
  --tests '*ModdedRecipeLayoutResolverTest' `
  --tests '*RecipeCapabilityResolverTest' `
  --tests '*RealRecipeEditorUiTest' `
  '-PrealModpack=G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh'
```

Для external tests требуется именно проверенный JAR с указанным SHA256. Runtime
merge test использует существующий genuine snapshot; без него этот opt-in набор
не является автономным CI-тестом. Обычные unit/integration suites не требуют JAR.

## Изменённые файлы задачи

```text
PHASE16_ARCHITECTURE_AUDIT.md
PHASE16_STEPS_1_4_REPORT.md
app/src/main/java/dev/recipeeditor/app/DefaultStaticAnalysisService.java
app/src/test/java/dev/recipeeditor/app/BotaniaRecipeAdapterTest.java
app/src/test/java/dev/recipeeditor/app/BotaniaRecipePersistenceTest.java
app/src/test/java/dev/recipeeditor/app/BotaniaTestArtifact.java
app/src/test/java/dev/recipeeditor/app/ModdedRecipeLayoutResolverTest.java
app/src/test/java/dev/recipeeditor/app/RecipeCapabilityResolverTest.java
desktop-ui/src/main/java/dev/recipeeditor/ui/MainWindow.java
desktop-ui/src/main/java/dev/recipeeditor/ui/ProcessingRecipeLayoutView.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeBrowserView.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeDisplayNameResolver.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeEditorDialog.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeLayoutModel.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeLayoutProvider.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeLayoutRenderer.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeLayoutResolver.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeSlotContent.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeSlotView.java
desktop-ui/src/main/java/dev/recipeeditor/ui/RecipeTypeDisplayNameResolver.java
desktop-ui/src/test/java/dev/recipeeditor/ui/RecipeLayoutAlignmentTest.java
integrations/src/main/java/dev/recipeeditor/integrations/ProjectRecipeAdapters.java
integrations/src/main/java/dev/recipeeditor/integrations/ProjectRecipeParsers.java
integrations/src/main/java/dev/recipeeditor/integrations/api/ModdedRecipeAdapter.java
integrations/src/main/java/dev/recipeeditor/integrations/api/ModdedRecipeAdapterRegistry.java
integrations/src/main/java/dev/recipeeditor/integrations/api/ModdedRecipeType.java
integrations/src/main/java/dev/recipeeditor/integrations/api/RecipeTypeCapabilities.java
integrations/src/main/java/dev/recipeeditor/integrations/botania/BotaniaJsonValues.java
integrations/src/main/java/dev/recipeeditor/integrations/botania/BotaniaRecipeAdapter.java
integrations/src/main/java/dev/recipeeditor/integrations/botania/BotaniaRecipeLayoutProvider.java
integrations/src/test/java/dev/recipeeditor/integrations/api/ModdedRecipeAdapterRegistryTest.java
integrations/src/test/java/dev/recipeeditor/integrations/api/ModdedRecipeParserTest.java
recipe-analyzer/src/main/java/dev/recipeeditor/recipeanalyzer/model/StandardDependencyAdapter.java
recipe-engine/src/main/java/dev/recipeeditor/recipeengine/BrewDefinition.java
recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeLayout.java
recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeMachineLayout.java
recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeOutput.java
recipe-engine/src/main/java/dev/recipeeditor/recipeengine/RecipeSemanticLocalization.java
```
