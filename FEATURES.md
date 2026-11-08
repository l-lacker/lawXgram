# Neogram features

What Neogram adds on top of Telegram for Android, where each feature lives, and what of
lawXgram is still to be ported. Stock behaviour that Neogram changes is always a patch in
`patches/` (see `series`); Neogram's own code is in `fork/`.

## Done

| Feature | Patches | Code |
|---|---|---|
| Neogram identity: app id `ru.llacker.neogram` (`.beta` for debug), name, icons, media folders, contacts account type, own API id/hash | `misc/build-support`, `misc/branding` | `fork/branding.properties`, `NeoBrand`, `fork/app` |
| Own strings for every language, brand passed as an argument | `misc/strings-pipeline` | `fork/res/values*/strings_neo.xml` |
| Own database tables next to the stock schema | `misc/database` | `NeoDatabase` |
| Settings hub "Neogram" in Settings, stock settings search, `tg://settings/neo/<page>/<option>` links | `misc/settings-ui` | `ru.llacker.neogram.settings` |
| Settings backup to a JSON file or Saved Messages, import from a file | — | `NeoBackup`, About page |
| Updates of direct builds from GitHub releases (APK per ABI, SHA-256 and signature checked) | — | `NeoUpdater`, `NeoApplicationLoader` |
| APK signature check of direct release builds (certificate taken from the release keystore at build time) | `misc/signature-check` | `fork/jni/colorado`, `NeoSignature` |
| Video messages: recorder choice per device with automatic fallback, battery- and heat-aware camera use | `feature/round-video-policy` | `NeoCameraPolicy`, `NeoRoundCamera` |
| Video messages: ultra-wide / main / telephoto lens button (CONTROL_ZOOM_RATIO) | `feature/round-video-lenses` | `NeoLensCatalog`, `NeoLensButton`, core `LensMath` |
| Video messages: 560×560 and 624×624 | `feature/round-video-quality` | core `RoundSpec` |
| Video messages settings page (recorder, cameras, lenses, quality) | — | `RoundVideoSection` |
| Send a gallery video as a video message | `feature/gallery-round` | `NeoGalleryRound`, core `RoundGeometry` |
| Fix: a silent converted video message was sent as a GIF | `bugfix/round-video-muted-gif` | — |

## To port from lawXgram

Grouped as in the plan; each becomes one or more small patches plus a settings page section.

- **Chats:** extra message menu items (repeat, Saved Messages, mention, details, copy photo/ID,
  JSON, history, delete downloaded file…), double-tap action, quick forward, search filter by
  type, sticker size and time on stickers, hide keyboard on scroll, next-channel jump, greeting
  sticker, channel buttons, business bot panel, Instant View links, blocked users' messages,
  delete-for-everyone default, confirmations before voice/video messages and calls, Markdown
  input with undo/redo, inline bots management.
- **Main screen and folders:** hide stories, archive on pull-down, hide "All chats", tab titles or
  icons, folder icons, tabs at the bottom, bottom navigation buttons, recent chats on long back.
- **Media:** instant camera / camera in gallery switches, voice message autoplay, volume-button
  unmute, autopause, prefer original quality, proximity sensor, download speed boost, GIF export,
  copy photo, voice enhancements.
- **Translator:** providers (Google, Yandex, DeepL, Microsoft, …), target language, auto
  translation, show original, keep formatting; transcription with Cloudflare Workers AI.
- **Privacy and accounts:** account passcodes, hidden accounts, panic code, instant lock,
  silence non-contacts, notification colour, name order, ID in profiles, IPv6, map drift fix.
- **Appearance:** tablet mode, app bar shadow, system fonts and weight fallback, emoji sets,
  seconds in times, no number rounding, predictive back, reduced colours, stroke on views.
- **Accessibility:** seek bar announcements, item counts, file progress, dialog types.
- **Accounts:** up to 8 accounts (native change), QR login.

## Dropped

- The helper-bot layer of lawXgram: cloud settings sync (replaced by file and Saved Messages
  backups), news, donor list, registration dates.
- DC ping through ping.neko, Nekogram meme features, the chat effects auto-reduce probe.
- lawXgram's own Camera2 fork for the attachment camera (the stock camera is used there).
- Client-side Premium bypasses and hidden sponsored-message buttons (against the API terms).
- Hacks for specific devices that the stock code no longer needs.
