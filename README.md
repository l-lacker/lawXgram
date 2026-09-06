# lawXgram

lawXgram is a third-party Telegram client based on Telegram for Android and the retained parts of the previous fork.

- Repository: https://github.com/l-lacker/lawXgram
- Releases: https://github.com/l-lacker/lawXgram/releases
- Issues: https://github.com/l-lacker/lawXgram/issues

## Build

1. Clone the repository with its submodules: `git clone --recursive --shallow-submodules https://github.com/l-lacker/lawXgram.git`
   - If you already cloned without `--recursive`, run `git submodule update --init --recursive --depth=1` inside the repository.
2. Create `local.properties` and fill in:
   - `sdk.dir`
   - `telegramApiId`
   - `telegramApiHash`
   - `storeFile`, `storePassword`, `keyAlias`, `keyPassword` for release signing
3. Create two Firebase Android apps with package names `ru.llacker.lawxgram` and `ru.llacker.lawxgram.beta`.
4. Download matching `google-services.json` files and place them into `TMessagesProj_App/src/release/google-services.json` for `ru.llacker.lawxgram` and `TMessagesProj_App/src/debug/google-services.json` for `ru.llacker.lawxgram.beta`.
5. Optional local integration keys can also be placed into `local.properties`:
   - `lawxPlaystoreAppUrl`
   - `lawxForceAnalytics`
   - `lawxSentryDsn`
   - `lawxTlvUrl`
   - `lawxTwpicBotUsername`
6. Build from Android Studio or with Gradle:
   - Debug/beta APK: `.\gradlew.bat :TMessagesProj_App:assembleDebug`
   - Release APK: `.\gradlew.bat :TMessagesProj_App:assembleRelease`
   - Convenience wrappers on Windows:
     - `.\scripts\assemble-debug.cmd` enables Gradle configuration cache and plain console output for debug builds
     - `.\scripts\assemble-release.cmd` enables Gradle configuration cache and plain console output for release builds
     - wrappers pause before closing when launched from a Windows console; set `LAWX_NO_PAUSE=1` for automated runs

## References

- Telegram API: https://core.telegram.org/api
- MTProto: https://core.telegram.org/mtproto
- Telegram Android translations: https://translations.telegram.org/en/android/
