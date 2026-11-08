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
| Message menu: repeat (+1), save to Saved Messages, set a reminder, copy photo, read a QR code from a photo, save a sticker to the gallery, open a video in another app, delete the downloaded file, post statistics, message details, hide Report | `hooks/chat-message-menu` | `NeoMessageMenu`, `NeoMessageActions`, `NeoMessageDetails`, `NeoQr` |
| Double tap on incoming / outgoing messages: reaction, reply, save, repeat, edit or nothing | `feature/double-tap` | `NeoDoubleTap` |
| Times with seconds, exact numbers instead of 12.3K, first/last name order | `feature/format-tweaks` | `NeoFormat` |
| Confirmation before a voice or video call | `feature/ask-before-call` | `NeoCalls` |
| Notifications without sound from people who are not contacts | `feature/silence-non-contacts` | `NeoNotifications` |
| Hide stories: the row above the chat list and in the archive, the post button and its hint | `debloat/stories` | `NeoDialogs` |
| Open the hidden archive with a pull down | `feature/archive-on-pull` | `NeoDialogs` |
| Hide the All Chats folder tab (shown again while reordering; archive in the chat list menu) | `feature/hide-all-chats-tab` | `NeoDialogs`, `NeoApplicationLoader` |
| Hide the time on stickers and large emoji | `debloat/sticker-time` | `NeoChat` |
| No pull up to the next channel or topic | `debloat/next-channel` | `NeoChat` |
| Hide the business bot panel above chats | `debloat/business-bot-panel` | `NeoChat` |
| ID row in user and chat profiles (Telegram API or Bot API form, data centre, copy on tap) | `feature/profile-id` | `NeoProfile` |
| No shadow under title bars | `debloat/app-bar-shadow` | `NeoAppearance` |
| Theme accent colour for message notifications | `feature/notification-color` | `NeoNotifications` |
| No media thumbnails in the chat list | `debloat/dialog-media-preview` | `NeoDialogs` |
| No camera tile in the attachment gallery | `debloat/gallery-camera` | `NeoMedia` |
| Voice and video messages do not play on by themselves | `debloat/voice-autoplay` | `NeoMedia` |
| Videos open in their original quality | `feature/original-video-quality` | `NeoMedia` |
| Hide the keyboard when the chat is scrolled | `feature/hide-keyboard-on-scroll` | `NeoChat` |
| "Delete for everyone" ticked by default | `feature/delete-for-all-default` | `NeoChat` |
| Try Instant View for every link | `feature/links-in-instant-view` | `NeoChat` |
| No greeting sticker in empty chats | `debloat/greeting-sticker` | `NeoChat` |
| Volume buttons only change the volume in chats (no video playback) | `debloat/volume-buttons-video` | `NeoMedia` |
| Voice messages: Telegram's new recorder and RNNoise noise suppression (stock settings that release builds hide), included in backups | — | `MediaSection`, `NeoBackup` |

## To port from lawXgram

Grouped as in the plan; each becomes one or more small patches plus a settings page section.

- **Chats:** forward without author or caption and quick forward, double tap to translate,
  search filter by type, sticker size, channel buttons, blocked users' messages, confirmation
  before sending voice/video messages (needs device testing: the stock pause-to-preview path
  also sends), Markdown input with undo/redo, inline bots management.
- **Main screen and folders:** tab titles or icons, folder icons, tabs at the bottom, bottom
  navigation buttons, recent chats on long back.
- **Media:** instant camera switch, proximity sensor during calls, download speed boost, GIF
  export.
- **Translator:** providers (Google, Yandex, DeepL, Microsoft, …), target language, auto
  translation, show original, keep formatting; transcription with Cloudflare Workers AI.
- **Privacy and accounts:** account passcodes, hidden accounts, panic code, instant lock, IPv6,
  map drift fix.
- **Appearance:** tablet mode, system fonts and weight fallback, emoji sets, predictive back,
  reduced colours, stroke on views.
- **Accessibility:** seek bar announcements, item counts, file progress, dialog types.
- **Accounts:** up to 8 accounts (native change), QR login.

## Dropped

- The helper-bot layer of lawXgram: cloud settings sync (replaced by file and Saved Messages
  backups), news, donor list, registration dates.
- DC ping through ping.neko, Nekogram meme features, the chat effects auto-reduce probe.
- lawXgram's own Camera2 fork for the attachment camera (the stock camera is used there).
- Client-side Premium bypasses and hidden sponsored-message buttons (against the API terms).
- Hacks for specific devices that the stock code no longer needs.
- Auto-pause of viewer videos: Telegram 13 pauses them itself when the app goes to the
  background. lawXgram's voice enhancement is replaced by the stock recorder settings above.
