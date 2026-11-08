# Neogram

Neogram is an unofficial Telegram client for Android built on the official
[Telegram for Android](https://github.com/TelegramOrg/Telegram-Android) sources.

It is maintained as a **patchset**: this repository holds only small, named patches
against a pinned upstream commit, Neogram's own code, and the tooling that puts them
together. The upstream sources are checked out and patched by a script, so updating
to a new Telegram release is a rebase of a short patch stack instead of a merge of
two diverged trees.

## Repository layout

| Path | Contents |
| --- | --- |
| `patchset.properties` | pinned upstream repository, commit and version |
| `series` | the order in which patches are applied (generated) |
| `patches/<group>/<name>.patch` | patches, grouped into `misc`, `hooks`, `feature`, `debloat`, `bugfix` |
| `fork/` | Neogram's own code and resources (wired into the build by a patch) |
| `tools/patchset.py` | the patchset tool (`patchset.cmd` / `patchset.sh` wrappers) |
| `docs/` | developer documentation |
| `worktree/` | *not tracked*: upstream checkout with the patches applied, open it in Android Studio |
| `local/` | *not tracked*: your secrets (API id/hash, signing, google-services, Sentry) |

## Requirements

- Git 2.35+ and Python 3.9+
- JDK 17 or newer
- Android SDK: platform 36, build-tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1
- Windows: enable long paths (`git config --global core.longpaths true` and the
  `LongPathsEnabled` registry switch) and keep the checkout path short, e.g. `C:\src\neogram`

`patchset doctor` checks all of the above.

## Getting started

```
git clone https://github.com/l-lacker/lawXgram neogram
cd neogram
patchset.cmd setup            # Windows
./patchset.sh setup           # Linux / macOS
```

`setup` downloads the pinned upstream commit with its submodules into `worktree/`
and applies every patch as one git commit. Open `worktree/` in Android Studio and
build `TMessagesProj_App` (variant `afatDebug` / `afatRelease`), or from the command line:

```
cd worktree
gradlew.bat :TMessagesProj_App:assembleAfatDebug          # Windows
./gradlew :TMessagesProj_App:assembleAfatDebug             # Linux / macOS
```

Add `-Pneo.abis=arm64-v8a` for a much faster single-ABI build.

Pure-Java logic lives in `fork/core` and has JVM unit tests that run in seconds:

```
worktree/gradlew -p fork/core test        # Windows: worktree\gradlew.bat -p fork\core test
```

### Local secrets

Secrets never enter the repository. Create `local/secrets.properties` (the `local/` folder
is ignored by git); environment variables `NEO_<KEY>` (e.g. `NEO_TELEGRAM_API_ID`) override it:

```
# https://my.telegram.org — required to log in
telegram.apiId=12345
telegram.apiHash=0123456789abcdef0123456789abcdef
# release signing (a release build fails without it; debug falls back to a public key)
signing.storeFile=local/release.jks
signing.storePassword=...
signing.keyAlias=...
signing.keyPassword=...
# optional
maps.apiKey=
sentry.dsn=
playstore.url=
```

Optional Firebase configs go to `local/google-services/{debug,release,play}.json`
(Firebase apps for `ru.llacker.neogram.beta` and `ru.llacker.neogram`); without them the app
builds and runs without Firebase.

## Everyday workflow

1. Edit stock files inside `worktree/` and Neogram files inside `fork/`.
2. Record stock changes as patches:
   - a new patch: `patchset new feature/my-change -m "short description" -a`
   - an addition to an existing patch: `patchset fixup feature/my-change -a`
3. `patchset export` writes the stack back to `patches/` and `series`.
4. Commit `patches/`, `series` and `fork/` in this repository.

`patchset status` shows the stack, `patchset check` verifies that the series applies
from scratch, `patchset lint` runs the static checks. See [docs/patchset.md](docs/patchset.md)
for the full guide (in Russian).

## Updating Telegram

```
patchset rebase latest        # or a specific upstream commit
```

Resolve conflicts in `worktree/` if any, run `patchset continue`, review the patches
listed as changed, build and test.

## License

GPL-2.0, the same as Telegram for Android. See [LICENSE](LICENSE).
