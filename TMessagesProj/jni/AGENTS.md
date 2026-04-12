# JNI

- This native build tree is used by normal app builds. Changes affect all ABIs.
- `CMakeLists.txt` is the entry point; keep build-graph changes deliberate and ABI-aware for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.
- Prefer local glue and wrapper files plus `tgnet/` before patching vendored trees.
- `ffmpeg/`, `boringssl/`, `third_party/`, `voip/`, `opus/`, `sqlite/`, `rlottie/`, and much of `exoplayer/` are vendor or prebuilt-heavy areas; avoid cleanup or reformat passes there.
- Source edits in vendor libraries may not affect runtime unless the committed per-ABI archives are rebuilt too.
- After JNI or CMake changes, run `./gradlew --no-daemon :TMessagesProj_App:assembleDebug`. Use `assembleRelease` too if packaging or linking changed.
- Never add machine-specific paths, secrets, or host-only tool assumptions.
