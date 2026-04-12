# TMessagesProj_AppTests

- This is an instrumentation-oriented test app. The module is commented out in root `settings.gradle` by default.
- Use this folder only for explicit test-app or schema or TL regression work.
- This is the main Kotlin area in the repo; do not infer repo-wide Kotlin conventions from it.
- Large `androidTest/.../generated` trees are generated or bulk data and should not be the first manual edit target.
- Keep its older SDK, NDK, and signing assumptions isolated from the main app modules.
- Call out when work here requires re-enabling the module in `settings.gradle`.
