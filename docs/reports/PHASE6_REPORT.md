# Phase 6 — Runtime Companion & Live Minecraft Integration

Дата проверки: 17 ноября 2026. Реализована Phase 6; Phase 7 не начата.

## Структура и архитектура

```text
RecipeEditor/
  app/                       Java 21, composition root, project persistence
  core/                      domain models
  modpack-parser/            read-only import
  registry/                  static resource/tag index
  recipe-engine/             static recipes, ChangeSet, validation/conflicts
  recipe-analyzer/
  integrations/
  exporters/
  companion-protocol/        Java 17, immutable wire DTOs, Gson, framing/auth/chunks
  companion-server/          Java 17, read-only TCP server, SnapshotProvider
  companion-client/          Java 21, connection, snapshot store, merge services
  desktop-ui/                JavaFX, Runtime panel and readonly recipe browser
  minecraft-companions/
    forge-1.20.1/            independent Java 17 / ForgeGradle build + wrapper
      src/main/             installable mod, registries, recipe adapters
      src/gametest/         opt-in real-game probes, excluded from normal JAR
  scripts/Verify-CompanionRuntime.ps1
```

Корень содержит 12 модулей. Циклов нет: protocol не зависит от domain/UI/Minecraft; server зависит от protocol; client — от protocol и recipe-engine; desktop-ui — от client и существующих domain-сервисов; app связывает реализации. Forge отдельно компилирует общие Java 17 protocol/server sources, не загружает Gradle 9 и desktop Java 21 зависимости. Сборщик мода использует Gradle 8.8, ForgeGradle 6.0.54, Forge 1.20.1-47.4.10, official Mojang mappings.

ForgeSnapshotProvider собирает Minecraft-объекты только на server thread, в tick jobs до 128 записей / 4 ms между записями. На сетевой worker передаются DTO, а не живые Minecraft-объекты. Смена server/datapack generation отменяет устаревший сбор. Игровой поток не пишет в socket. Один клиент и ограниченная очередь исключают параллельные неограниченные сканирования.

Static + runtime merge сохраняет статические оригиналы и пользовательский ChangeSet. RegistryMergeService предпочитает подтверждённые runtime записи; статические ресурсы без подтверждения сохраняют свою достоверность. RecipeMergeService сравнивает доказуемые общие поля, учитывает различие RecipeType и JSON serializer ID. Неизвестная семантика остаётся диагностикой. Доказанный конфликт UPDATE/DELETE или коллизия CREATE использует существующий quarantine/source conflict; сохранённые и несохранённые изменения не теряются. Runtime normalized properties не выдаются за исходный JSON и не поступают в редактор/экспорт как оригиналы.

## Протокол v1

TCP только `127.0.0.1`, случайный свободный порт. Session credentials: UUID, случайный 256-bit token и nonce, owner-only файл `config/mre_companion/session.json`; секрет меняется при старте процесса. Mutual challenge использует HMAC-SHA256 с role/session/clientNonce/serverNonce; токен не передаётся открыто и не отображается в UI/логах.

Envelope: `version`, `type`, `requestId`, `payload`; длина UTF-8 JSON — 4-byte prefix. Сообщения: HANDSHAKE/HANDSHAKE_RESPONSE, GET_REGISTRIES/REGISTRY_DATA, GET_TAGS/TAG_DATA, GET_RECIPES/RECIPE_DATA, GET_MOD_LIST/MOD_LIST_DATA, SYNC_PROGRESS/SYNC_COMPLETED, ERROR, PING/PONG, CANCEL, DISCONNECT. Ошибки включают INVALID_REQUEST, UNSUPPORTED_REQUEST, BUSY, SYNC_FAILED. Никаких путей записи, исполняемого кода, игровых команд или remote reload в API нет.

Максимум: frame 1 MiB, record 8 MiB, dataset 128 MiB, combined client budget 256 MiB; fragments 49152 bytes. Проверяются request/snapshot IDs, sequence, SHA-256 raw framed records, byte/record counts, единая среда всех datasets, final generation/ready. Strict JSON отвергает duplicate keys, неизвестные envelope fields, неверный UTF-8, trailing JSON, глубину >48. Коллекции ограничены: registry/recipes 200000, tags 100000, mods 4096. Неизвестная версия отклоняется. Таймауты: connect 3 s, handshake 5 s, read 15 s, sync 120 s, server idle 120 s; heartbeat 5 s. Cancel/EOF закрывает socket и отменяет сбор; автоматического reconnect нет.

Snapshot — ZIP `.mresnapshot` ровно с manifest и четырьмя JSONL datasets; без извлечения архивных путей. Manifest содержит версии, окружение, время, полноту, counts/hash и diagnostics. Запись: временный файл, force, повторная валидация, backup, atomic replace с восстановлением при fallback. Частичные данные не заменяют полный snapshot. Ошибки целостности, ссылки и небезопасные IDs отклоняются. Checksums проверяют целостность, но не являются подписью доверенного Minecraft.

## Матрица данных

| Данные | Реализация |
|---|---|
| Items | Реальные registered IDs, имя/ключ локализации, класс, BlockItem metadata |
| Blocks | Registered IDs, runtime class, имена state properties и допустимые значения без Cartesian product |
| Fluids | Реальные IDs, source/flowing flags для FlowingFluid; разные IDs не склеиваются |
| Recipe types / serializers | Отдельные реальные реестры и классы |
| Tags | Server registry tags items/blocks/fluids, включая пустые resolved tags |
| Mod list | ID, версия, имя из работающего Forge |
| Vanilla | Exact shaped/shapeless, smelting/blasting/smoking/campfire, stonecutting, smithing transform/trim |
| Generic mod recipes | ID, type/serializer/class/source и явная неизвестность; inputs/outputs не угадываются |
| GTCEu 7.1.4 | Проверенный специализированный adapter, PARTIALLY_SUPPORTED |

Vanilla передаёт загруженные ingredients/result/count/NBT, cooking time/experience, dimensions/category/group где API доступен. Trim динамический; приватные роли smithing не восстанавливаются. Подклассы/custom ingredients могут требовать отдельного адаптера.

GTCEu adapter изолирован от обязательной зависимости: только фиксированные публичные API/поля через reflection, без setAccessible и произвольных имён классов из сети. Проверена именно установленная **7.1.4**. Используются публичный codec загруженного GTRecipe, category, duration, input/output EU, capabilities item/fluid, tick content, conditions, data. Chance/maxChance/tierChanceBoost читаются явно из публичного Content, включая нулевые defaults, опускаемые codec. Non-consumable входы остаются отдельными. Обход включает RecipeManager и GT category/proxy collections; разные определения с одинаковым ID сохраняются в runtimeVariants, без объявления победителя машины. Другие версии получают generic metadata и диагностику. Полный исходный GT JSON, исполняемые условия и callback-семантика не реконструируются. GregTechMaterialCollector — только контракт будущего расширения; Material Registry/editor сейчас не заявлены.

## Проверки и сборка

| Проверка | Результат |
|---|---|
| `./gradlew.bat clean build` | BUILD SUCCESSFUL, 1m 2s |
| `./gradlew.bat test` | BUILD SUCCESSFUL, 4s; задачи UP-TO-DATE после clean build |
| JUnit XML общего прогона | 225 total: 210 passed, 15 skipped, 0 failures/errors |
| `./gradlew.bat buildCompanion` | BUILD SUCCESSFUL, 1m 27s; child build 39s |
| `./gradlew.bat :app:run` | JavaFX запущен; Runtime controls наблюдались через Windows accessibility |
| Опциональные adapter tests по реальному GT snapshot | 8 passed: 6 GT + 2 Vanilla |

15 обычных skips: 9 новых opt-in runtime/adapter проверок, 4 ранее существующих real-pack проверки, 2 platform-dependent symlink проверки. Пропуски не считаются успешными Minecraft-тестами. Отдельные opt-in запуски реально запускали Minecraft:

| Реальная среда Forge 47.4.10 | Registry entries (все 5 kinds) | Tags | Recipes | Mods |
|---|---:|---:|---:|---:|
| Vanilla Forge GameTest Server | 2294 | 556 | 1174 | 3 |
| Minecraft Client + Integrated Server | 2294 | 556 | 1174 | 3 |
| GTCEu 7.1.4 + минимальные embedded dependencies, GameTest Server | 16825 | 5335 | 35194 | 6 |

GT primary adapter records: **26112**. Во всех трёх средах проверены настоящее TCP-подключение/auth, registry/tags/recipes/mods, сохранение/чтение snapshot, повторная синхронизация и reconnect. Vanilla и integrated прогоны подтвердили обнаружение остановки Minecraft heartbeat-ом. Integrated запуск дополнительно выполнил настоящий datapack reload через локальный test-only hook и проверил увеличение generation >1; production network API такой команды не содержит. В result.txt записана среда первого snapshot generation=1, до reload.

Свидетельства: `.manual-tests/phase6/evidence/{vanilla,integrated,gt}-runtime-result.txt`, реальный `gt-runtime.mresnapshot`. Это изолированный GT runtime, **не полный gameplay-прогон Cosmic Frontiers**. Тестовый клиент использовал копию нашего тестового мира. Исходные пользовательские миры и моды не изменены; SHA-256 GT source до/после: `8450217d1aaae9645a6ea645897222cee27f13e9f163ec113a2a1d9f0c390f37`.

Runtime UI наблюдался с Connect/Disconnect/Sync/Cancel/Open Snapshot/View Snapshot, реестрами и диагностикой. Полный end-to-end merge через ручные JavaFX клики не прогонялся; merge/ChangeSet сохранность проверены тестами. Открытое пользователем приложение и его проект оставлены в его распоряжении.

## Готовый JAR и установка

`minecraft-companions/forge-1.20.1/build/libs/mre_companion-forge-1.20.1-0.1.0.jar`

78211 bytes, 39 classes, все class-file major **61 (Java 17)**. Тестовые GameTests/ClientIntegrationHooks, JavaFX и GT библиотеки отсутствуют. SHA-256: `b34f07c9a961c338ada6c2968b22a7c18daa459b13253014314c38523ae116f6`.

1. Установите JDK 21 для Desktop и JDK 17 для Minecraft/Forge build. Java 17 для Forge 1.20.x описана в [официальной документации Forge](https://docs.minecraftforge.net/en/1.20.x/gettingstarted/).
2. Скопируйте готовый JAR в `mods` отдельного Minecraft 1.20.1 Forge 47.x инстанса. Мод не требует GTCEu. Ничего не копируйте из gt-reference.
3. Запустите Minecraft, откройте мир/локальный сервер. Session-файл появится в `<game directory>/config/mre_companion/session.json`.
4. Из корня редактора: `./gradlew.bat :app:run`. Runtime → Session File → выберите этот файл → Connect → Synchronize with Minecraft.
5. Для merge откройте/импортируйте именно ту же директорию Minecraft. Для независимого просмотра используйте View Snapshot. Cached snapshot явно исторический.
6. После Minecraft restart выполните ручной Connect с новым session-файлом. После reload выполните новую синхронизацию.

Команды воспроизведения, wrapper details и convenience-script описаны в README. Сам новый PowerShell convenience-script не был отдельно исполнен; реальные проверки запускались эквивалентными Gradle-командами. Для GameTest используется [официальный механизм Forge](https://docs.minecraftforge.net/en/1.20.1/misc/gametest/).

## Ограничения

- Только Minecraft 1.20.1 Forge 47; Fabric/NeoForge и другие Minecraft версии не реализованы.
- Read-only live integration: редактирование runtime/world, удалённый reload и автоматическая установка не реализованы.
- Подтверждение относится к snapshot environment/time. Начальное перечисление коллекций и преобразование одной большой записи не прерываются бюджетом 4 ms; нельзя гарантировать отсутствие единичных задержек у произвольного мода.
- Произвольные изменения модом на месте вне Forge lifecycle/reload событий не гарантируют транзакционный snapshot всего мира.
- GTCEu поддержка частичная: нормализованное состояние не равно исходному рецепту или полной машинной семантике. UI/diagnostics это показывают.
- Desktop heap по умолчанию 2 GiB, opt-in большие runtime tests 1536 MiB. Фиксированные лимиты могут отвергнуть очень большую сборку; неполный snapshot не применяется.
- Существующий static TagIndex ограничивает JSON одного тега 2 MiB; merge отклоняется при неполном tag index.
- Owner-only credentials защищают от других обычных пользователей ОС; процессы того же пользователя/администратор не изолируются. SHA-256 snapshots не доказывает происхождение.
- Единовременное соединение с одним Companion; нет автоматического reconnect и глобального постоянно обновляемого registry.
- Build имеет предупреждение deprecated FMLJavaModLoadingContext.get(); JavaFX classpath выдаёт существующее unnamed module warning. Сборку/запуск они не блокируют.

Полный перечень новых файлов и изменённых точек интеграции: PHASE6_FILES.txt.
