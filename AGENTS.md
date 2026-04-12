# lawXgram AGENTS

## Scope
- Applies repo-wide. Deeper `AGENTS.md` files override this file for their subtree.
- `.git/info/exclude` currently ignores `AGENTS.md`, so these instructions are local-only unless that rule is narrowed or files are force-added.

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

## Local Codemagic Notes

This section is local-only and excluded from git through `.git/info/exclude`.

### Access
- Read `/Users/kirill/lawXgram/.codemagic.local.env` first.
- Source it before any Codemagic API call.
- The repo in Codemagic is `kirillshsh/lawXgram`.
- The Codemagic app id is `69dbb63284334e85be5011af`.
- The default workflow is `android-debug` on branch `main`.

### Build Flow
- Trigger a remote build with the Codemagic REST API.
- Poll the build status until it succeeds or fails.
- Download the debug APK artifact from Codemagic.
- If an Android phone is connected over `adb`, install with `adb install -r <apk>`.

Useful shell pattern:
```sh
set -a
source "/Users/kirill/lawXgram/.codemagic.local.env"
set +a
```

Build trigger example:
```sh
curl -sS \
  -H "Content-Type: application/json" \
  -H "x-auth-token: $CM_API_TOKEN" \
  -d "{\"appId\":\"$CODEMAGIC_APP_ID\",\"workflowId\":\"$CODEMAGIC_WORKFLOW_ID\",\"branch\":\"$CODEMAGIC_BRANCH\"}" \
  https://api.codemagic.io/builds
```

Fetch latest build metadata:
```sh
curl -sS \
  -H "Content-Type: application/json" \
  -H "x-auth-token: $CM_API_TOKEN" \
  https://api.codemagic.io/apps
```

Get latest successful universal APK URL for a phone:
```sh
APPS_JSON=$(curl -sS -H "Content-Type: application/json" -H "x-auth-token: $CM_API_TOKEN" https://api.codemagic.io/apps)
ARTIFACT_URL=$(printf '%s' "$APPS_JSON" | ruby -rjson -e 'data = JSON.parse(STDIN.read); app = data.fetch("applications").find { |a| a["_id"] == ENV.fetch("CODEMAGIC_APP_ID") }; build = data.fetch("builds").find { |b| b["appId"] == app["_id"] && b["status"] == "finished" && b["fileWorkflowId"] == ENV.fetch("CODEMAGIC_WORKFLOW_ID") }; artifact = build.fetch("artefacts").find { |a| a["name"].include?("universal.apk") } || build.fetch("artefacts").find { |a| a["name"].include?("arm64-v8a.apk") } || build.fetch("artefacts").find { |a| a["type"] == "apk" }; print artifact.fetch("url")')
```

Get latest successful x86_64 APK URL for the local Android emulator:
```sh
APPS_JSON=$(curl -sS -H "Content-Type: application/json" -H "x-auth-token: $CM_API_TOKEN" https://api.codemagic.io/apps)
ARTIFACT_URL=$(printf '%s' "$APPS_JSON" | ruby -rjson -e 'data = JSON.parse(STDIN.read); app = data.fetch("applications").find { |a| a["_id"] == ENV.fetch("CODEMAGIC_APP_ID") }; build = data.fetch("builds").find { |b| b["appId"] == app["_id"] && b["status"] == "finished" && b["fileWorkflowId"] == ENV.fetch("CODEMAGIC_WORKFLOW_ID") }; artifact = build.fetch("artefacts").find { |a| a["name"].include?("x86_64.apk") } || build.fetch("artefacts").find { |a| a["name"].include?("x86.apk") } || build.fetch("artefacts").find { |a| a["type"] == "apk" }; print artifact.fetch("url")')
```

Download and install on a connected phone:
```sh
curl -L -H "x-auth-token: $CM_API_TOKEN" -o /tmp/lawxgram.apk "$ARTIFACT_URL"
adb install -r /tmp/lawxgram.apk
```

If multiple Android devices are connected, use `adb devices` and then:
```sh
adb -s <serial> install -r /tmp/lawxgram.apk
```

After updating local env values, keep them local and never commit them.
