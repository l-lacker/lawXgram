# TMessagesProj_App

- This module is intentionally thin. Most feature logic belongs in `:TMessagesProj`, not here.
- Keep this folder focused on packaging concerns: manifest glue, signing, Google Services, Sentry, ABI splits, and output naming.
- Preserve the current optional `google-services.json` behavior so local builds still work without Firebase config.
- Never commit signing material or values sourced from `local.properties`.
- Be careful with `applicationIdSuffix`, ABI split settings, output filenames, and release minify or shrink settings.
- Verify with `assembleDebug`, and use `assembleRelease` when release behavior is touched.
