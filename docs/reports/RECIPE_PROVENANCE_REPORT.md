# Recipe source detection and provenance

18 ноября 2026.

## Почему minecraft:tnt показан с biomesoplenty

Причина установлена чтением реального архива ISG:

- Archive: `G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh\minecraft\mods\BiomesOPlenty-1.20.1-18.0.0.592.jar`.
- Archive SHA-256: `c587b49931e62f89c2673936a5e7269c6daabb713dbcc30813cb0f76da37e01d`.
- В `META-INF/mods.toml` архива объявлен `modId="biomesoplenty"`.
- В этом же архиве действительно существует `data/minecraft/recipes/tnt.json`, type `minecraft:crafting_shaped`, output `minecraft:tnt`. SHA-256 JSON: `aa56447ab88becff1db35cd5c6add8a0bfea29a57c9c9cc8f81fdc23ad2efa63`.

ResourceScanner получает ID модов из metadata **текущего** ModScanResult и создаёт отдельный immutable ResourceSource для каждой записи. StaticRecipeScanner берёт namespace `minecraft` из внутреннего пути, а metadata контейнера — из ResourceSource. Поэтому сочетание namespace=minecraft и archive mod=biomesoplenty соответствует найденному файлу. Оно не доказывает авторство рецепта и не устанавливает активного Minecraft load-order winner.

Доказательства: `.manual-tests/provenance/biomesoplenty-tnt-evidence.json`, `real-provenance-result.txt`; отдельный external JUnit test повторяет импорт и индексацию реальной сборки. Исходные файлы сборки в этой задаче не изменялись.

## Результаты аудита

| Подозрение | Результат |
|---|---|
| Последний обработанный JAR присваивается последующим рецептам | Не обнаружено: scanArchive принимает archive/mods параметрами, ResourceSource копирует список. Добавлен тест чередующихся источников |
| Совпадающие Recipe ID перезаписываются | RecipeIndex хранит Map<ID,List<RecipeDefinition>> и сохраняет все определения |
| Namespace используется как Source Mod | В сканере и индексах поля уже разделены. UI не предоставлял явного выбора обоих режимов и называл container metadata «source ownership» |
| Static/runtime merge теряет статические источники | Списки сохранялись. Теперь merge также возвращает отдельную provenance-модель; runtime-источник не наследует моды статических кандидатов |
| Источник берётся из соседней записи | Не обнаружено; сканер создаёт source внутри цикла по документам. Покрыто тестом |
| Ошибочный Map Key | RecipeChangeSet проверяет равенство key и change.id; добавлен тест отказа для несовпадения |
| ChangeSet уничтожает исходные ссылки | originals сохранялись. Найдена потеря source replacement: capture хранил только JSON, materialize заменял source на project overlay |

## Изменения

- `RecipeSource`: явные archiveModIds/sourceModIds, declaringSource, physicalSource, sourceFile, originalFile, confidence. Старое поле modIds сохранено для совместимости; оно обозначает metadata физического контейнера, не автора.
- `RecipeProvenance` и `RecipeSourceResolver`: отдельные Recipe ID/namespace, список кандидатов, activeDefinition и overriddenDefinitions со scope STATIC_DISCOVERY / PROJECT / PROJECT_CONFLICT / RUNTIME. Статический скан, даже с одним кандидатом, не назначает runtime-победителя. Project overlay затеняет originals только в проекте. Заблокированная правка не считается активной и не объявляет источники переопределёнными.
- Неизвестный Declaring Source всегда `UNKNOWN`. Физический JAR, namespace, совпадение JSON или порядок сканирования не используются как доказательство авторства/приоритета. Для runtime показываются sourceInstance, snapshot ID/time и полученные sourceInformation; нормализованные properties/type/serializer и прочие metadata сохраняются в RuntimeData.Recipe.
- RecipeIndex предоставляет раздельные byNamespace/bySourceMod и provenance без удаления конфликтующих определений. Старый byMod сохранён как совместимый lookup по source metadata.
- `StoredRecipeChange`, `ProjectDocument`, `FileProjectPersistence`: optional replacementSources сохраняет исходные координаты replacement. Это добавочное поле формата 1; старые проекты читаются, их originals удерживаются целиком, отсутствующий replacement source не угадывается по первому original или namespace.
- `ExportSourceMapping` используется и планировщиком, и UI. PlannedRecipe содержит provenance, namespace экспортируемого файла по-прежнему определяется Recipe ID. Данные source не переименовывают output namespace.
- Export Wizard: отдельные Recipe Namespace, Source Mod IDs (container metadata), Active Source, Physical Source, Original File; вкладка Sources / provenance со всеми кандидатами/derived-from и scoped overrides. Данные можно просматривать и для строки плана, включая explicit unchanged copies.
- Recipes, Runtime browser, Export Wizard: раздельные режимы фильтра Recipe Namespace / Source Mod. Runtime с неизвестным модом попадает в UNKNOWN, даже если существует статический кандидат из стороннего JAR.
- Отображение project members использует `project.json#changes/minecraft:tnt`: логический ключ не передаётся в Windows Path.resolve.

## Проверки

Добавлены RecipeSourceResolverTest, RecipeProvenanceTest, ExportSourceMappingTest; расширены RecipeMergeServiceTest и ProjectPersistenceTest. Добавлен external RealRecipeProvenanceTest.

Проверяются vanilla/source namespace, сторонний namespace, minecraft namespace внутри стороннего JAR, чередование источников, несколько определений, datapack-кандидат без выдуманного приоритета, неизвестный runtime source, сохранение всех origins после edit/Undo/Redo/save/reopen, legacy fallback, неверный ChangeSet key, экспорт по Recipe ID и блокировка конфликтующей правки.

Итоговая команда `gradlew.bat build test :app:packageWindows`: **BUILD SUCCESSFUL**, 3m 51s, 73 executed tasks. Unit/component + reproducible integration: **281 tests, 279 passed, 2 skipped, 0 failures/errors**. Два пропуска относятся к созданию symlink в Windows. Добавлено 12 regression cases относительно предыдущего набора.

Отдельный `:app:externalTest --tests '*RealRecipeProvenanceTest' -PrealModpack=...` на финальных классах: **1 passed**, 0 skipped/failures/errors. Повторный SHA-256 BOP JAR совпадает с указанным выше.

Свежий app-image запущен и проверен через computer-use:

- Старый `gui-project.json` формата 1 открыт без пересохранения; повторный импорт сохранил оба определения TNT и обнаруженный source conflict.
- Export Wizard: визуально подтверждены раздельные namespace=minecraft и container metadata=biomesoplenty; Sources / provenance показывает BOP archive/internal path, UNKNOWN declaring/active source и PROJECT_CONFLICT. Выборов экспорта/записи файлов в этом GUI smoke не выполнялось.
- Recipes: поиск minecraft:tnt показывает две строки; режим Recipe Namespace переключается отдельно от Source Mod. При Source Mod=biomesoplenty остаётся только одна строка из JAR. Datapack-строка не получает BOP ID из соседнего определения.

Логи: `.manual-tests/provenance/final-build.log`, `external-test.log`. Исправленный portable launcher: `app/build/windows/image/MinecraftRecipeEditor/MinecraftRecipeEditor.exe`; нужен весь каталог app-image.

Дополнительная попытка `clean build test releaseWindows` не завершилась: Windows заблокировала удаление старого EXE установщика из artifacts, используемого процессом 19564. Этот процесс не завершался принудительно. После этого обычные build/test/packageWindows успешно пересобрали все 73 задачи. Новые MSI/installer bundle этим прогоном не подготовлены; старый installer EXE не соответствует исправленному app-image. Блокировка clean не объявляется успешной clean-сборкой. Подробности: `clean-build-release.log`.

## Ограничения

Фактическая авторская принадлежность JSON внутри BOP JAR и порядок приоритетов Minecraft этой задачей не устанавливаются. В ISG также обнаружено второе статическое определение TNT в datapack ранее созданного тестового мира. Оба источника сохраняются; активность datapack в других мирах из наличия файла не выводится. Companion protocol остаётся read-only и не сообщает доказанный оригинальный pack/resource для каждого runtime-рецепта — соответствующий источник остаётся UNKNOWN.
