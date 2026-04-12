# scripts

- These are Windows-only wrappers around root Gradle builds.
- Keep commands aligned with `README.md` and `.github/workflows/android-debug.yml`.
- `assemble-debug.cmd` uses configuration cache. `assemble-release.cmd` disables it.
- Keep wrappers thin and free of machine-specific logic.
