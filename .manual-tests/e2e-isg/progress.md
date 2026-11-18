# ISG E2E — итоговый статус

Подробный актуальный отчёт: L:\My Add\RecipeEditor\E2E_ISG_REPORT.md.

- GUI import, indexing, edit TNT count=2, Undo/Redo, save/reopen, AUTO→DATAPACK Preview/Review/Export выполнены. Выбор папки, первое сохранение и Output потребовали помощи пользователя.
- Настоящий Minecraft 1.20.1 / Forge 47.4.28 запущен через Prism. Добавлен собственный Companion 0.1.0.
- Аутентификация и полные снимки успешны: baseline 98 937 рецептов, после datapack+reload 100 398.
- В after-datapack.mresnapshot подтверждён minecraft:tnt, Vanilla SUPPORTED, result.count=2. В baseline этого ID нет.
- Все 70 исходных JAR имеют прежний SHA-256. Существующий мир не открывался.
- Отдельный MRE E2E 20261118 сохранён через меню, затем нажата Quit Game.
- Найдено и исправлено зависание на тысячах TextArea.appendText; настоящий повторный merge прошёл, UI отвечает, 16 216 сообщений опущены из представления.
- Clean build успешен: 269 tests, 267 passed, 2 skipped, 0 failures/errors.
- External: 4 passed; 6 GT-specific skipped из-за GTCEu 8 вместо поддержанного 7.1.4.
- Другие ограничения перечислены в E2E_ISG_REPORT.md. Полная release validation и поддержка GT 8 не заявляются.
