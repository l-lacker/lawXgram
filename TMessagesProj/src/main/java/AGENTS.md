# Java Source

- This tree is Java-first. There is no active Kotlin app code here.
- First-party code lives under `org.telegram.*` and `ru.llacker.lawxgram.*`.
- Trees under `com.*`, `androidx.*`, `me.*`, and `org.webrtc.*` are vendor or fork territory; avoid drive-by edits and reformatting there.
- Prefer lawX-specific changes in `ru.llacker.lawxgram` when practical.
- Rebuild the debug app after touching shared core classes.
