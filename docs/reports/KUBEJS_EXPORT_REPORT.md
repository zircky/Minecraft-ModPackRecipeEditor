# KubeJS JavaScript / Data export

18 ноября 2026. Целевая среда: Minecraft 1.20.1 Forge, KubeJS 2001.6.4-build.120 и 2001.6.5-build.26. Полная совместимость произвольных KubeJS/модовых сериализаторов не заявляется.

## Причина и проверенные API

Предыдущий KubeJsRecipeExporter уже генерировал ServerEvents.recipes, exact-ID remove и event.custom(...).id(...), но отказывал любой версии кроме 2001.6.4-build.120. В ISG установлена 2001.6.5-build.26. Ещё не было отдельного Data JSON механизма и выбора режима в профиле/Wizard. Recipe namespace minecraft не является причиной отказа и не используется для определения мода-источника.

Перед реализацией проверены официальные материалы:

- [Recipes](https://kubejs.com/wiki/tutorials/recipes): callback, exact-ID remove и custom JSON.
- [Server scripts](https://kubejs.com/wiki/folder-structure/server-scripts): загрузка при world load/reload.
- [Data](https://kubejs.com/wiki/folder-structure/data): каталог работает как data внутри datapack.
- [Структура каталогов](https://kubejs.com/wiki/folder-structure).
- Исходники ветки [2001 RecipesEventJS](https://github.com/KubeJS-Mods/KubeJS/blob/2001/common/src/main/java/dev/latvian/mods/kubejs/recipe/RecipesEventJS.java) и [RecipeJS](https://github.com/KubeJS-Mods/KubeJS/blob/2001/common/src/main/java/dev/latvian/mods/kubejs/recipe/RecipeJS.java): custom, remove(IDFilter), id(ResourceLocation).

Дополнительно javap проверил сигнатуры в настоящем kubejs-forge-2001.6.5-build.26.jar. Вывод: `.manual-tests/kubejs-export/kubejs-2001.6.5-api.txt`. API других веток/новых Minecraft не использованы.

## Архитектура

Существующий RecipeChangeSet и четыре внешних ExportFormat сохранены: AUTO, DATAPACK, KUBEJS, CRAFTTWEAKER. Регистрация экспортёров и транзакционный ExportFileWriter не переписаны.

- KubeJsRecipeExporter — единственный зарегистрированный facade KUBEJS. Он планирует один механизм для каждого изменения.
- KubeJsJavaScriptExporter — JS CREATE/UPDATE/DELETE, выделенный файл `kubejs/server_scripts/recipe_editor_<profile>_generated.js`. UPDATE удаляет только точный ID и добавляет одну новую версию с тем же ID; DELETE содержит только exact-ID remove.
- Для девяти Vanilla-сериализаторов используется проверенный `event.custom(JSON.parse(serializedJson)).id(originalId)`. Это сохраняет JSON-структуру/свойства и избегает потерь при преобразовании в сокращённые shaped/cooking builders. Вымышленные методы не генерируются. JS-строки сериализуются и экранируются, включая Unicode line separators.
- KubeJsDataExporter реализует механизм KUBEJS_DATA: CREATE/UPDATE в `kubejs/data/<namespace>/recipes/<nested-path>.json`, с исходным JSON. DELETE UNSUPPORTED, без пустого JSON и без скрытого перехода к JS.
- RecipeDataPathResolver содержит явное сопоставление версии с каталогом. Реализована 1.20.1 → recipes; неподдержанные версии отклоняются, включая версии с другой datapack layout.
- KubeJsExportMode: AUTO, JAVASCRIPT, DATA_JSON, COMBINED. AUTO/COMBINED направляют совместимые Vanilla additions/updates в Data, а удаления/проверенные JS-only адаптеры в JS. COMBINED создаёт оба типа файлов только когда набор изменений этого требует; пустой второй файл не создаётся.
- KubeJsRecipePlan фиксирует KUBEJS_DATA либо KUBEJS_JAVASCRIPT для одного ID; повторный ID в batch отклоняется. PlannedRecipe показывает фактический механизм и target files, включая пустые targets для UNSUPPORTED.
- Режим сохраняется в ExportProfile/sidecar. Для старых профилей без поля режима сохраняется исторический JAVASCRIPT, без скрытого изменения поведения.

Тип/операция/версия/семантика проверяются независимо от Recipe ID namespace и Source Mod. GT JSON не становится Data-совместимым только из-за наличия raw JSON. Старый GTCEu 7.1.4 JS codec остаётся ограничен старым проверенным KubeJS build; GT 8 и неизвестные кастомные сериализаторы не объявлены поддержанными.

## Неизвестные поля

Raw JSON не фильтруется при генерации. Для известных Vanilla-сериализаторов отдельно перепроверяется проекция известных обязательных полей. Обычные неизвестные root keys сохраняются как неиспользуемые Vanilla metadata; это не семантическая поддержка таких полей. Namespaced extension keys/conditions и непроверенные ingredient/result extensions отклоняются с диагностикой. Нормализованное runtime-состояние не принимается за оригинальный JSON. Сериализация сама по себе не считается runtime-проверкой.

## Preview, UI и безопасность

Wizard добавляет KubeJS Export Mode (виден для KUBEJS/AUTO), показывает operation/type, фактический mode, target file, capability и причины отказа. Список плана сохраняет неподдерживаемые изменения. Generated files показывает содержимое, существующие hashes, replacements и obsolete files; текущий Review gate остаётся обязательным.

Data JSON имеет обязательные имена, поэтому не получает случайного profile prefix. Владение определяется manifest/hash. Неизвестные существующие файлы и пользовательские правки блокируют запись. ExportPaths дополнен только безопасными Data recipe paths. Windows-special paths, traversal, symlinks, stale preview, rollback и source-directory consent продолжают проверяться. Переключение Data→JS удаляет прежний owned Data-файл транзакционно, исключая дублирование старых артефактов; пользовательские скрипты не перезаписываются.

## Тесты и runtime

Добавлены KubeJsJavaScriptExporterTest, KubeJsDataExporterTest, RecipeDataPathResolverTest, KubeJsExportCapabilityTest, KubeJsCombinedExportTest, KubeJsExportWizardTest. Последний проверяет контракт профиля/preview, а не выдаётся за rendered JavaFX test. Покрыты девять Vanilla serializers, CREATE/UPDATE/DELETE, tags/counts, nested paths/namespaces, unknown fields, unsupported codecs, explicit Data DELETE rejection, unique mechanism per ID, manifest re-export/user edits, mode switch и legacy profiles.

`gradlew.bat build test :app:packageWindows --max-workers=2`: **BUILD SUCCESSFUL**, 4m 12s. **302 tests: 300 passed, 2 skipped, 0 failures/errors**. Пропуски — недоступное создание Windows symlink. Добавлен 21 regression case. Новый portable app-image собран; installers не пересобирались.

Для реальной проверки подготовлены три экспорта вне сборки обычным RecipeExportService с manifest: `.manual-tests/kubejs-export/runtime/{create,update,delete}`. Используются отдельный ранее созданный мир MRE E2E 20261118 и установленный Companion 0.1.0:

1. JS CREATE `mre_export_test:vanilla_lifecycle`, dirt → 2 cobblestone.
2. Data UPDATE существующего `biomesoplenty:jacaranda_planks`, 4 → 5 planks, исходный ID и путь сохранены.
3. JS DELETE `minecraft:tnt` только по точному ID.

Это независимые реальные рецепты с известными источниками для UPDATE/DELETE. Исходный JS-рецепт не подменяется выдуманной ссылкой на JSON-файл для обхода source validation. Runtime assertions читают реальный Server RecipeManager через Companion и сохраняют complete snapshots; генерация файлов и отсутствие ошибок в логе отдельно от этих assertions.

Подготовка экспортов: external KubeJsRuntimeExportPreparationTest **passed**. JS CREATE: live assertion **passed**, snapshot `create.mresnapshot`, 98 939 рецептов; custom output count=2, planks baseline count=4. Data UPDATE после настоящего `/reload`: live assertion **passed**, snapshot `update.mresnapshot`, 98 939 рецептов; planks count=5, custom JS recipe count=2. Forge=47.4.28, KubeJS=2001.6.5-build.26. Stage DELETE и финальная очистка фиксируются по завершении.
