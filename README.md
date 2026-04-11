# lawXgram

lawXgram is a third-party Telegram client based on Telegram for Android and the retained parts of the previous fork.

- Repository: https://github.com/l-lacker/lawXgram
- Releases: https://github.com/l-lacker/lawXgram/releases
- Issues: https://github.com/l-lacker/lawXgram/issues

## Build

1. Clone the repository: `git clone https://github.com/l-lacker/lawXgram.git`
2. Create `local.properties` and fill in `storeFile`, `storePassword`, `keyAlias`, `keyPassword` for release signing.
3. Create two Firebase Android apps with package names `ru.llacker.lawxgram` and `ru.llacker.lawxgram.beta`.
4. Download matching `google-services.json` files and place them into `TMessagesProj_App/src/release/google-services.json` for `ru.llacker.lawxgram` and `TMessagesProj_App/src/debug/google-services.json` for `ru.llacker.lawxgram.beta`.
5. Create `TMessagesProj/src/main/java/ru/llacker/lawxgram/Extra.java` from `Extra.java.example` and fill the required values.
6. Build from Android Studio or with Gradle:
   - Debug/beta APK: `.\gradlew.bat :TMessagesProj_App:assembleDebug`
   - Release APK: `.\gradlew.bat :TMessagesProj_App:assembleRelease`

## References

- Telegram API: https://core.telegram.org/api
- MTProto: https://core.telegram.org/mtproto
- Telegram Android translations: https://translations.telegram.org/en/android/
