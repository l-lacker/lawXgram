# TMessagesProj

- This is the main product module. Most runtime code, resources, and JNI integration live here.
- `src/main` is the baseline source set. `src/debug` and `src/play` are variant-only overlays.
- Put product behavior here, not in `TMessagesProj_App`, unless the task is specifically packaging or distribution related.
- New config should flow from root Gradle and `local.properties` wiring, not hardcoded secrets or machine-specific paths.
- Verify most changes with `./gradlew --no-daemon :TMessagesProj_App:assembleDebug`.
- Never edit `build/`, `.cxx/`, or generated outputs by hand.
