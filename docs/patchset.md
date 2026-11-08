# Как устроен патчсет Neogram

Neogram — не форк в классическом смысле, а набор маленьких патчей поверх официального Telegram for
Android. В репозитории лежат только:

- `patchset.properties` — какой коммит upstream взят за основу;
- `series` и `patches/` — сами патчи и порядок их применения;
- `fork/` — наш собственный код и ресурсы;
- `tools/` — скрипт, который собирает всё вместе.

Полное дерево Telegram с применёнными патчами появляется в `worktree/` (не коммитится). Каждый патч
в `worktree/` — это отдельный git-коммит, поэтому с ними можно работать обычными средствами git и
Android Studio.

## Подготовка (Windows)

1. Установить Git for Windows (2.35+), Python 3.9+ (`py -3`), Android Studio.
2. В SDK Manager поставить: Android 16 (API 36), Build-Tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1.
3. Включить длинные пути:
   - `git config --global core.longpaths true`;
   - в реестре `HKLM\SYSTEM\CurrentControlSet\Control\FileSystem\LongPathsEnabled = 1`.
4. Клонировать репозиторий в короткий путь, например `C:\src\neogram`.
5. `patchset.cmd doctor` — проверит окружение.
6. `patchset.cmd setup` — скачает upstream с сабмодулями в `worktree\` и применит патчи.
7. Открыть `worktree\` в Android Studio (именно папку worktree, не корень репозитория).

## Повседневная работа

- Код Neogram (`fork/`) правится как обычно и коммитится в основной репозиторий.
- Правки стокового кода делаются в `worktree/`, после чего:
  - новый патч: `patchset new feature/имя -m "short description" -a`;
  - дополнение существующего: `patchset fixup feature/имя -a`;
  - затем `patchset export` и коммит `patches/` + `series` (+ `fork/`) в основной репозиторий.
- `patchset status` — список патчей, их размер и что ещё не экспортировано (`*`).
- `patchset find TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` — какие патчи трогают файл.

Имя патча: `<группа>/<имя-через-дефис>`, группы — `misc`, `hooks`, `feature`, `debloat`, `bugfix`.
Описание в subject — короткое и на ASCII, подробности — в `--body` (можно по-русски).

### Изменить старый патч

- Проще всего: правка поверх текущего состояния и `patchset fixup <патч> -a` — коммит сам «вольётся»
  в нужный патч.
- Если нужно увидеть патч в его исходном окружении: `patchset edit <патч>` остановит стек на нём;
  правка, `git add`, `git commit --amend --no-edit`, затем `patchset continue`.
- Переставить/переименовать/удалить: `patchset move <патч> --after <другой>`, `patchset rename`,
  `patchset reword`, `patchset drop`.

После любой из этих команд — `patchset export`.

### После `git pull`

Если кто-то (или CI) обновил патчи, `patchset sync` перестроит `worktree/` под новые `patches/`.
Если в worktree есть неэкспортированные коммиты, sync откажется — сначала `patchset export`.

## Обновление Telegram

```
patchset rebase latest          # или конкретный коммит upstream
```

1. Скрипт скачивает новый коммит и переносит все патчи на него (`git rebase`).
2. При конфликте он останавливается: разрешить конфликт в `worktree/` (Android Studio это умеет),
   `git add` файлы, `patchset continue`. Отменить всё — `patchset abort`.
3. В конце обновляются `patchset.properties` и сабмодули, патчи экспортируются, и печатается список:
   - «content changed» — патчи, чьё содержимое поменялось при переносе, их нужно просмотреть;
   - «only moved» — сдвинулся только контекст;
   - «disappeared» — патч стал пустым (upstream сделал то же самое) и выпал из series.
4. Собрать, проверить на устройстве, закоммитить. Подробный порядок для агентов —
   `.claude/skills/neogram-rebase/SKILL.md`.

## Проверки

- `patchset export --check` — `patches/` совпадает с worktree (ставится как pre-commit через
  `patchset install-hooks`).
- `patchset check --sparse` — series применяется с нуля на чистом upstream и патчи в каноническом
  виде; то же самое делает CI.
- `patchset check --drop feature/x` — остальные патчи применяются без `feature/x` (патч удаляем).
  Ночной workflow `nightly.yml` проверяет так каждый патч feature/debloat/bugfix.
- `patchset lint` — запрещённые конструкции (`true ||`, рефлексия, ключи API, импорты форк-кода в
  стоке, бинарники, новые файлы в `org/telegram`), пересечения и откаты между патчами, размеры
  hook/bugfix-патчей, правила строк.

## Почему патчи выглядят именно так

Экспорт детерминированный: одинаковый стек даёт побайтно одинаковые файлы на любой машине.
- Нет `Date:`, автор и коммит обнулены, хэши в строках `index` заменены нулями — иначе любая правка
  одного патча меняла бы соседние.
- Бинарных диффов нет: заменить стоковую картинку можно только через `fork/app/res`
  (ресурсы app-модуля перекрывают библиотечные).
- Байты сохраняются как есть: у нескольких стоковых файлов окончания строк CRLF, поэтому `*.patch`
  помечены в `.gitattributes` как `-text`.

## Типичные проблемы на Windows

- «Filename too long» — не включены длинные пути или слишком длинный путь к репозиторию.
- «Unlink of file failed» при rebase/fixup — файл держит Gradle-демон или Android Studio. Скрипт сам
  вызывает `gradlew --stop` перед переписыванием истории; если не помогло — закрыть Android Studio
  и повторить `patchset continue`.
- Антивирус сильно замедляет работу с `worktree/` — добавить папку в исключения.
