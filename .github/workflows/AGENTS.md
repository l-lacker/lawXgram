# GitHub Workflows

- `android-debug.yml` is the reference CI build for this repo.
- Keep CI Java, SDK, NDK, and CMake versions aligned with the root Gradle files.
- Materialize secrets at runtime; never hardcode them into workflow files.
- The workflow builds `:TMessagesProj_App:assembleDebug`, so keep CI and local verification guidance in sync.
- CI currently triggers on `push` and `workflow_dispatch`, not `pull_request`.
