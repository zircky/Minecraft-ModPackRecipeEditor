# Resource Browser, textures, localization and GT materials

Проверено на реальной `G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh`: Minecraft 1.20.1, Forge 47.4.28, GTCEu `8.0.0-SNAPSHOT+e24b238`. Использован только тестовый мир `MRE E2E 20261118`.

## Установленные причины

1. `FluidResourceIndexer` находил только явные fluid-tag references. Ни ссылки на sprite, ни tint из клиента не передавались; PNG сама по себе не подтверждала регистрацию.
2. `ResourceScanner` игнорировал `.png.mcmeta`, а `TextureResourceService` возвращал весь PNG. Вертикальный spritesheet мог попасть в UI целиком.
3. `ModelTextureResolver` поддерживал только generated/handheld/cube_all. Layer1, более сложные parent chains, variants и multipart давали одинаковый пустой результат без объяснения.
4. `RegistryMergeService` заменял `localizedNames` на пустую map. Browser затем игнорировал runtime displayName. Кроме того, ключ `tagprefix.ingot` без аргумента материала является шаблоном, а не названием конкретного слитка.
5. `GregTechMaterialCollector` был незарегистрированным интерфейсом. Статические assets не содержат полного каталога динамических GT-форм.
6. Перенос длинных названий увеличивал карточки. Во время GUI-проверки также обнаружены массовое перечисление Materials через accessibility и интерпретация `_` как JavaFX mnemonic в кнопках форм.

## Реализация

Существующий scanner/indexer/merge pipeline сохранён. `ResourceAssetCatalog` индексирует модели, физические текстуры и single/directory atlas mappings. `ModelTextureResolver` проходит родителей, разрешает переменные, generated layers и block-item references. Для blockstates выбирается явно помеченный репрезентативный образец; сложная геометрия имеет `RENDERING_UNSUPPORTED`. При неизвестном порядке нескольких физических определений используется `AMBIGUOUS_SOURCE`, произвольный победитель не выбирается.

`ResourceVisual` хранит статус, слои, известный ARGB tint и при наличии SHA256 активной runtime PNG. `FluidTextureResolver` использует только явные ссылки. `RuntimeResourceResolver` соединяет собранные ссылки с assets, не создавая регистрации из имён файлов. Source/flowing fluid IDs, still/flowing sprites, atlas и texture-pack metadata сохраняются отдельно.

`TextureResourceService` остаётся единственным сервисом чтения/подготовки изображений. Он проверяет PNG и размеры, читает `.png.mcmeta`, выбирает первый указанный кадр, применяет tint и композицию слоёв. Несовпадение с известным runtime SHA256 блокирует показ статической копии. Один worker, очередь 128, LRU 64 готовых PNG + 64 кадров размером не больше 256×256, максимум четыре открытых ZIP. Очередь, кэши и архивы освобождаются при смене проекта/закрытии. JavaFX не читает архивы и PNG на Application Thread.

`LocalizationService` использует реальные ключи и Minecraft Component с аргументами, en_us/ru_ru, английский fallback и затем ID. Незаполненные `%s` и оставшиеся в runtime-названии translation keys не считаются переводом. Старые переводы сохраняются при совместимом фактическом ключе. Неоднозначные статические переводы не получают случайного победителя. Для Material.name язык берётся из явно указанного единственного языка клиентского snapshot; без такого свидетельства или при смешанных языках он не угадывается. Преобразования ID в Title Case в редакторе нет.

`RegistryIndex` сохраняет assets и отдельный список `MaterialDefinition`. Материал не является Item. Collector читает публичный API проверенного GT8: Material Registry, properties и доступные scalar getters, flags, components, формулу, MaterialEntry/Ore Prefix и FluidStorageKey. Формы содержат только реальные Forge registry IDs; Snapshot проверяет каждую ссылку по kind + ID. Другие GT builds не включаются автоматически. Поддержка материалов не активирует несовместимый адаптер редактирования рецептов GT8.

Companion собирает клиентские sprite/color/name DTO на клиентском потоке, остальные registry/material DTO — на server thread, ограниченными порциями на тиках. `materials-v1` согласуется через handshake capabilities; wire protocol остаётся v1. Snapshot schema v2 добавляет checksummed `materials.jsonl`; schema v1 читается. Старый Desktop v2-снимок не откроет. Выявленный при живом тесте idle timeout исправлен: синхронизация отправляет PING, PONG обрабатываются отдельно от dataset frames; общий deadline 300 секунд, серверный idle timeout 360 секунд.

UI: отдельная Materials navigation, виртуализированные страницы по 60 ресурсов, фиксированные карточки/строки, ellipsis и Tooltip с полными значениями, адаптивные колонки Items/Blocks/Fluids, панель изображения и evidence. Namespace и Source Mod — разные режимы фильтра. Неизвестный runtime owner отображается как UNKNOWN. View Recipes / View Uses используют раздельные item/fluid индексы и отключены при отсутствии разобранных ссылок. Form navigation открывает конкретный зарегистрированный предмет, блок или жидкость.

## Реальные замеры до/после

`before.json` снят до производственных изменений. Финальные данные — `.manual-tests/resources/completed.json`, свежий runtime snapshot — `.manual-tests/resources/isg-materials.mresnapshot`. Числа означают доступные плоские PNG-превью/образцы и готовые подписи выбранного языка; это не количество полностью отрендеренных Minecraft-моделей. Для материалов изображение является образцом зарегистрированной формы.

Статический каталог:

| Каталог | Записей | Превью до → после | en_us до → после | ru_ru с en fallback до → после |
|---|---:|---:|---:|---:|
| Items | 12 049 | 5 247 → 7 898 | 3 976 → 3 957 | 3 978 → 3 959 |
| Blocks | 13 092 | 1 444 → 8 025 | 5 179 → 5 179 | 5 183 → 5 183 |
| Fluids | 551 | 0 → 0 | 2 → 2 | 2 → 2 |

У Items перестали считаться готовыми названиями 19 строк с незаполненными параметрами перевода. Статические жидкости остаются tag evidence: без явной визуальной связи текстура не угадывается.

После объединения с runtime (включая оставшиеся статические кандидаты):

| Каталог | Записей | Превью до → после | en_us до → после | ru_ru с en fallback до → после |
|---|---:|---:|---:|---:|
| Items | 31 332 | 5 247 → 23 700 | 83 → 16 557 | 84 → 29 511 |
| Blocks | 17 963 | 1 444 → 11 773 | 10 → 8 068 | 14 → 10 752 |
| Fluids | 5 241 | 0 → 5 214 | 0 → 1 932 | 0 → 5 233 |
| Materials | 3 083 | недоступно → 3 076 | недоступно → 657 | недоступно → 3 083 |

Runtime подтвердил 29 442 Item, 11 286 Block и 5 235 Fluid. Остальные строки сохраняют статическую достоверность. Получено 3 083 материала и 28 286 ссылок на формы; ошибок преобразования материалов в diagnostics нет. Английские переводы, отсутствующие в доступных language assets, не подменяются русскими или придуманными названиями.

## Проверка

- `build test verifyArchitecture :app:packageWindows` — успешно; 323 unit/integration-теста: 321 passed, 2 symlink-проверки skipped, 0 failures/errors.
- Отдельная Java 17 Forge-сборка `jar test` — успешно; в Forge-модуле unit-тестов нет, фактический collector проверен внешним тестом клиента.
- `GregTechMaterialCollectorTest.verifyMaterialCollectorAgainstActualIsgClient` — passed на живой ISG. Проверены iron properties/forms, все registered references, propane sprite/tint/key, динамический aluminium sprite. Snapshot сохранён и повторно прочитан при аудите.
- `ResourceBrowserAuditTest` — passed на реальных assets + snapshot. Конечная сборка вместе с аудитом: `completed-verification.log`, BUILD SUCCESSFUL, 4m 17s.
- Добавлены/расширены `FluidTextureResolverTest`, `ItemTextureResolverTest`, `BlockTextureResolverTest`, `LocalizationServiceTest`, `TextureInheritanceTest`, `GregTechMaterialCollectorTest`, `ResourceBrowserViewModelTest`, merge/roundtrip и отдельность item/fluid lookup. TCP regression проверяет collector дольше серверного idle timeout.
- GUI production-компонентов на настоящем snapshot: Aluminium Ingot / Алюминий (Слиток), Пропан, preview/details/Tooltip, Materials search/pagination, свойства и формы алюминия. Form Dust открыла именно `nuclearcraft:aluminum_dust`, а не ID по шаблону. Недоступные действия отключены.
- Пересобранный Windows app-image запущен по проверенному абсолютному пути; главное окно и Materials empty state работают. Данные проверялись через test-classpath preview тех же production-компонентов, без нативного file chooser и без фиктивных ресурсов.

## Ограничения

Это не полный Minecraft Render Engine. Геометрия, item overrides, custom renderers, generated/stitched/paletted atlas sprites без доступной PNG и biome/position-dependent tint могут оставаться неподдержанными. Общие vanilla client assets вне выбранного инстанса не сканируются. В финальном runtime-каталоге жидкостей 20 ссылок не найдены в доступных assets, 7 требуют дополнительных runtime texture metadata. Причины доступны в UI; все текстуры поддержанными не объявляются.

Каталог материалов проверен для конкретного установленного GT8 build. Не заявлена совместимость других версий, полный набор stack/NBT-вариантов и полное редактирование материалов/GT8-рецептов. Локализация en_us и ru_ru может иметь разное покрытие.

## Запуск и воспроизведение

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat :app:run
```

Готовый EXE: `app/build/windows/image/MinecraftRecipeEditor/MinecraftRecipeEditor.exe`. Companion: `minecraft-companions/forge-1.20.1/build/libs/mre_companion-forge-1.20.1-0.1.0.jar`. После обновления Companion требуется перезапуск Minecraft и новый Runtime snapshot.

```powershell
.\gradlew.bat :companion-client:externalTest --tests '*GregTechMaterialCollectorTest' '-PruntimeSessionFile=G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh\minecraft\config\mre_companion\session.json' '-PresourceRuntimeSnapshot=L:\My Add\RecipeEditor\.manual-tests\resources\isg-materials.mresnapshot'
.\gradlew.bat :app:externalTest --tests '*ResourceBrowserAuditTest' '-PrealModpack=G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh' '-PresourceAuditDirectory=L:\My Add\RecipeEditor\.manual-tests\resources' '-PresourceAuditSnapshot=L:\My Add\RecipeEditor\.manual-tests\resources\isg-materials.mresnapshot' '-PresourceAuditLabel=recheck'
```

Живой тест требует открытого неприостановленного локального тестового мира. Credentials не печатаются. `:app:previewResources` принимает `-PrealModpack=... -PruntimeSnapshotFile=...` и существует только в test classpath.

Minecraft после проверки штатно закрыт с сохранением тестового мира. В ISG установлен финальный Companion SHA256 `7bc8775659a4d73727beafd3b1a3754fadbe3eafc1dd28d847ca3730bc201f3b`; предыдущий JAR сохранён в `.manual-tests/resources/companion-before.jar`. Два собственных KubeJS-файла прошлой проверки удалены только после совпадения SHA256, их копии сохранены рядом. Пользовательские моды и другой мир не изменялись.
