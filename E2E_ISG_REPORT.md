# Проверка ISG через GUI и Minecraft

Дата: 18 ноября 2026. Desktop: 1.0.0-alpha.1, статус релиза **NOT READY**.

Проверена сборка `G:\PrismLauncher\instances\ISG-0.6.0-alpha.1-harsh` с разрешения пользователя. Это проверка перечисленных сценариев, не подтверждение всех функций будущих фаз.

## Среда

- Minecraft 1.20.1, Forge 47.4.28, Java 17 для клиента.
- 80 модов до добавления Companion, 81 после; 70 исходных JAR.
- Companion 0.1.0, protocol 1; desktop использует bundled Java 21.
- GTCEu 8.0.0-SNAPSHOT, runtime сообщает `8.0.0-SNAPSHOT+e24b238`. Семантический адаптер GT 7.1.4 неприменим.
- KubeJS 2001.6.5-build.26. CraftTweaker отсутствует.

## Выполненные сценарии

| Сценарий | Результат и доказательство |
|---|---|
| Запуск настоящего клиента через Prism | Главное меню и отдельный игровой мир открыты |
| Импорт и индексация | 25 692 ресурса, первоначально 11 489 статических определений рецептов |
| Изменение существующего рецепта | В GUI выбран `minecraft:tnt`, источник biomesoplenty; result.count изменён на 2 через Raw JSON, Apply JSON и Save to project |
| Undo / Redo | В GUI наблюдались 0 Modified после Undo и 1 Modified после Redo |
| Сохранение проекта | `.manual-tests/e2e-isg/gui-project.json`, UPDATE minecraft:tnt, count=2 |
| Повторное открытие | Выполнено через Open Project; экспорт прочитал сохранённую правку. После исправления редактор ещё раз самостоятельно открыл файл кликами по папкам |
| Preview / Review / Export | AUTO выбрал DATAPACK: 1 поддержанная правка, 2 файла, итог SUCCESS |
| Подключение Companion | Аутентификация настоящего локального Minecraft через Runtime → Connect |
| Полная синхронизация | Исходный снимок: 98 937 рецептов. Первый запрос при паузе мира завершился таймаутом; повтор без паузы успешен |
| Установка экспорта | Только в datapacks нового тестового мира `MRE E2E 20261118`, существующие файлы не заменялись |
| Reload | Команда `/reload` введена и отправлена через игровой чат; клиент сообщил `Reloaded with no KubeJS errors!` |
| Проверка фактического результата | Второй полный снимок: 100 398 рецептов. `minecraft:tnt` присутствует, adapter=vanilla-1.20.1, status=SUPPORTED, result.item=minecraft:tnt, result.count=2 |
| Проверка сохранённого снимка | После перезапуска Open Snapshot загрузил `.manual-tests/e2e-isg/after-datapack.mresnapshot`, проверил его и слил с открытым проектом |
| Сохранность источников | SHA-256 всех 70 исходных JAR совпадают с резервным списком |
| Завершение мира | Через меню Save and Quit вернулись в главное меню; затем нажата Quit Game |

Папка экспорта называется `export-kubejs`, но её содержимое — **DATAPACK**, выбранный AUTO. KubeJS-экспорт этим прогоном не подтверждён. Физическая установка datapack и проверка файлов/снимков выполнялись файловыми инструментами; операции редактирования, Undo/Redo, экспорта и запуска синхронизации — через GUI.

В исходном полном runtime-снимке ID `minecraft:tnt` отсутствовал. Поэтому результат нельзя описывать как изменение действующего рецепта с 1 на 2: статический JSON существовал, а runtime-рецепт появился после установки datapack и reload. Причина отсутствия исходного ID не устанавливалась.

После установки datapack повторная статическая индексация нашла 11 490 определений и один source conflict сохранённой правки. Редактор заблокировал её для проверки; файл проекта не пересохранялся поверх исходного тестового результата.

## Найденные и исправленные проблемы

1. **Зависание JavaFX после успешного runtime merge.** Thread dump показал цикл `MainWindow.applyRuntime → RuntimeIntegrationView.addDiagnostic → TextArea.appendText`. Тысячи отдельных вставок блокировали UI. Теперь сообщения добавляются пакетами, до 200 сообщений / 64 000 символов за пакет, с количеством опущенных сообщений. На настоящем снимке повторное слияние завершилось, интерфейс отвечал; диагностика показала 16 216 опущенных сообщений.
2. После сохранения проекта обновляется сводка состояния Recipes: ранее она могла продолжать показывать «not saved», хотя файл уже сохранён. Исправление скомпилировано и проверено общей сборкой; отдельный повтор GUI Save после этой малой правки не выполнялся.
3. При принятии снимка обновляется строка Runtime status: ранее она могла оставаться «CONNECTED — no snapshot» при заполненных счётчиках. Исправление проверено сборкой; отдельный GUI-прогон последней правки строки статуса не выполнялся.

Изменены `desktop-ui/src/main/java/dev/recipeeditor/ui/MainWindow.java`, `RuntimeIntegrationView.java`, `RecipeBrowserView.java`.

## Автоматические проверки

- `gradlew.bat clean build`: SUCCESS, 1m 53s, 82 tasks. Сначала clean был заблокирован работающим EXE; после штатного закрытия повтор прошёл.
- `gradlew.bat test`: SUCCESS в завершающем запуске вместе с выбранными external suites.
- Обычные unit/component + reproducible integration suites: 269 тестов, 267 passed, 2 skipped, 0 failures/errors. Пропуски связаны с недоступным созданием symlink в Windows.
- External: RealStaticAnalysisTest и RealRecipeEditingTest на указанной ISG — 2 passed.
- External: RuntimeRecipeAdapterTest на настоящем after-datapack.mresnapshot — 2 passed (Vanilla cooking properties; неизвестные рецепты без выдуманных семантических полей).
- GregTechRuntimeRecipeAdapterTest: 6 skipped из-за несовместимого GT 8; поддержка GT 8 не объявляется.
- Архитектурная проверка: 12 production modules, без циклических зависимостей.
- Финальный `releaseWindows` с локальным WiX 3: SUCCESS, 2m 27s. Пересозданы app-image, EXE/MSI/ZIP и manifest; отдельная Forge-сборка Companion также прошла (test NO-SOURCE, это не GameTests).
- `Verify-ReleaseArtifacts.ps1`: проверены размеры и SHA-256 всех четырёх файлов нового bundle, результат VERIFIED; лог `artifact-verification.log`.
- Финальный smoke-запуск свежего app-image: главное окно 1.0.0-alpha.1 открылось после полной пересборки, оставлено открытым без проекта.

Логи: `.manual-tests/e2e-isg/clean-build.log`, `final-tests.log`, `release-build.log`.

## Артефакты и оставленные изменения

- Доказательства: `.manual-tests/e2e-isg/evidence-summary.json`, `baseline.mresnapshot`, `after-datapack.mresnapshot`, thread dumps, проект и export metadata.
- Новый JAR в пользовательской сборке: `minecraft/mods/mre_companion-forge-1.20.1-0.1.0.jar`. SHA-256: `c3af2c26063f68df4e311de6bd53fd59c3cb7306379f1dc967c54dde1a2a92a3`.
- Companion создал `minecraft/config/mre_companion/session.json`; секрет не выводился и в отчёт не копировался.
- Новый тестовый мир `minecraft/saves/MRE E2E 20261118` с datapack `recipe_editor_147a0422c59e453fb2b269415304ae8d` сохранён. Существующий пользовательский мир не открывался.
- Резервный список исходных JAR и instance.cfg: `.manual-tests/e2e-isg/before`. Резервирование instance.cfg выполнено после первого запуска, а не до всех действий Prism.
- Исправленный переносимый launcher: `app/build/windows/image/MinecraftRecipeEditor/MinecraftRecipeEditor.exe`. Нужен весь каталог app-image.
- Новый локальный alpha bundle: `app/build/windows/artifacts/1.0.0-alpha.1/`. Актуальные размеры/хеши находятся в его `release-manifest.json` и `SHA256SUMS.txt`; таблица старого PHASE10_REPORT.md относится к предыдущей сборке.

## Пределы проверки

Пользователь помог с выбором папки импорта, первым сохранением и заполнением Output, поскольку драйвер не направлял ввод в дочерние/нативные окна. Остальные перечисленные действия выполнялись инструментами. Драйвер также не смог заполнить поиск в дочернем View Snapshot; точный TNT count=2 подтверждён содержимым настоящего снимка, а не поиском в этом окне.

Не подтверждены этим прогоном: все типы рецептов и экспорта, фактический крафт на верстаке, длительная стабильность, upgrade/downgrade installers, все варианты отмены/сбоев. Проверки Create/Mekanism, полного Analyzer UI и GT 8 нельзя объявлять успешными при отсутствии соответствующей реализации/совместимости. Состояние **NOT READY** сохраняется.
