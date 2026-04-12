# ru.llacker.lawxgram

- This package is the fork-specific customization layer. Prefer placing lawX-only behavior here before patching upstreamish `org.telegram.*` code.
- Keep settings and config aligned with `LawxConfig`, `LawxEnvironment`, helpers, and existing settings screens.
- New user-visible lawX strings should start in `TMessagesProj/src/main/res/values/strings_lawx.xml`.
- Do not hardcode DSNs, API keys, bot usernames, or machine-specific URLs; keep them wired through Gradle and `BuildConfig`.
- Reuse existing settings patterns such as `BaseLawxSettingsActivity`, `UItem`, and `UniversalAdapter`.
