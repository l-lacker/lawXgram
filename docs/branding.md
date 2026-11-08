# Бренд Neogram: где что лежит

Всё, что называет приложение или идентифицирует его, собрано в нескольких местах, чтобы
переименование или новая графика не требовали правки кода и патчей.

## Значения — `fork/branding.properties`

| Ключ | Что это | Куда попадает |
| --- | --- | --- |
| `app.name`, `app.nameBeta` | имя приложения (не переводится) | `resValue AppName/AppNameBeta`, `BuildConfig.NEO_APP_NAME*`, `NeoBrand.appName()` |
| `app.applicationId`, `app.applicationIdSuffixDebug` | идентификатор пакета | `applicationId`, тип аккаунта и mime-типы контактов (генерируются из него) |
| `app.apkBaseName` | префикс имён APK | `Neogram-<версия>-<код>-<abi>.apk` |
| `app.mediaFolder` | папка в Pictures/, Download/, Movies/, Music/ | `NeoBrand.mediaFolder()` |
| `links.*` | ссылки в «О Neogram» (пустое значение скрывает строку) | `NeoBrand.*Url()` |
| `updates.githubRepo` | откуда direct-сборки берут обновления | `NeoBrand.updatesRepository()` |
| `signing.releaseCertSha256` | отпечаток сертификата release-ключа | проверка APK в CI |

В строках (`fork/res/values*/strings_neo.xml`) имя приложения никогда не пишется — оно
подставляется аргументом `%1$s` (`NeoStrings.get()`), поэтому перевод не может его изменить.

## Графика — `fork/app/res`

Ресурсы app-модуля перекрывают одноимённые ресурсы Telegram, поэтому для замены достаточно
положить файлы с теми же именами.

| Файлы | Назначение |
| --- | --- |
| `mipmap-*/ic_launcher.webp`, `ic_launcher_round.webp`, `ic_launcher_foreground.webp` | иконка лаунчера (и для Android < 8) |
| `mipmap-anydpi-v26/ic_launcher*.xml` | adaptive icon: фон `@color/neo_launcher_background` + передний план |
| `values/neo_brand.xml` | цвета фона иконки и сплэша |
| `drawable/tg_splash_320.xml` | иконка сплэш-экрана Android 12+ (заменяет самолётик Telegram) |
| `drawable-*/notification.png` | значок уведомлений в статус-баре |
| `drawable-*/book_logo.png` | логотип в шорткатах шеринга |

Сейчас это временные иконки lawXgram. Иконки разделов хаба настроек (`fork/res/drawable/neo_section_*.xml`)
— перекрашенные стоковые значки, бренда не содержат.

## Что ещё остаётся от Telegram

- Анимация самолётика в стартовом интро (`IntroActivity`, нативный `IntroRenderer`) — заголовок уже
  «Neogram», сама анимация пока стоковая.
- Альтернативные иконки Telegram (Vintage, Aqua, Premium, Turbo, Nox) не поставляются: их alias
  удаляет манифест-оверлей `fork/app/AndroidManifest.xml`, а `LauncherIconController` знает только
  иконку по умолчанию.
