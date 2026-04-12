# lawXgram AGENTS

## Scope
- Applies repo-wide. Deeper `AGENTS.md` files override this file for their subtree.
- Keep machine-specific notes in local root files, not in shared `AGENTS.md`.

## Project Overview
- `TMessagesProj/` is the main Android module. Most Java app code, resources, and JNI integration live there.
- `TMessagesProj_App/` is the thin application wrapper for packaging, signing, Google Services, Sentry, ABI splits, and APK naming.
- `TMessagesProj_AppTests/` is an instrumentation-oriented test app, but it is commented out in `settings.gradle` by default.
- `scripts/` contains Windows Gradle wrappers.
- `Tools/` contains manual browser-based asset helpers.
- `.github/workflows/android-debug.yml` is the current CI reference build.

## Commands
- Default verification: `./gradlew --no-daemon :TMessagesProj_App:assembleDebug`
- Faster local debug: `./gradlew :TMessagesProj_App:assembleDebug --configuration-cache --configuration-cache-problems=warn`
- Release only when the task needs release behavior: `./gradlew :TMessagesProj_App:assembleRelease --no-configuration-cache`
- Use `TMessagesProj_AppTests` only for explicit instrumentation or test-app work, and only after re-enabling the module in `settings.gradle`.

## Code Style
- Follow the surrounding Java-first Telegram style: 4-space indent, same-line braces, minimal targeted diffs.
- Keep most product logic in `TMessagesProj`; keep `TMessagesProj_App` thin.
- Prefer lawX-specific behavior in `ru.llacker.lawxgram` before patching upstreamish `org.telegram.*` when practical.
- Reuse existing UI and settings patterns such as `BaseFragment`, `UItem`, `UniversalAdapter`, `LayoutHelper`, and `Theme`.
- Preserve upstream file headers in upstream-derived files.

## Resources
- Base lawX strings live in `TMessagesProj/src/main/res/values/strings_lawx.xml`.
- Localized `values-*/strings_lawx.xml` files are Crowdin-managed; avoid mass-editing them unless the task is translation maintenance.
- Keep debug-only resource overrides in `TMessagesProj/src/debug/res`.
- Do not rename raw resources or assets without checking code references.

## Boundaries
- Never commit or expose `local.properties`, `.codemagic.local.env`, `google-services.json`, `sentry.properties`, keystores, or other local secrets.
- Ask before changing application IDs, signing, Firebase or Sentry wiring, ABI splits, APK naming, `play`-specific permissions, Gradle toolchains, or major dependencies.
- Treat vendor trees as high-risk: `TMessagesProj/src/main/java/com/**`, `TMessagesProj/src/main/java/org/webrtc/**`, `TMessagesProj/jni/third_party/**`, `TMessagesProj/jni/ffmpeg/**`, `TMessagesProj/jni/boringssl/**`, `TMessagesProj/jni/voip/**`.
- Do not hand-edit `build/`, `.cxx/`, generated outputs, or committed prebuilt native archives unless the task explicitly requires it.
