# scripts

- These are Windows-only wrappers around root Gradle builds.
- Keep commands aligned with `README.md` and `.github/workflows/android-debug.yml`.
- `assemble-debug.cmd` and `assemble-release.cmd` use configuration cache and force `--console=plain`.
- Keep wrappers thin and free of machine-specific logic.
- Keep routine builds on the wrappers' default Gradle properties and output directories so configuration-cache entries stay reusable.
- Use `LAWX_NO_PAUSE=1` for automated shell runs; leave it unset for double-clicked Windows console windows.
