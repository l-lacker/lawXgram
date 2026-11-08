---
name: neogram-rebase
description: Update Neogram to a newer Telegram for Android release. Moves the patch stack onto a new upstream commit with tools/patchset.py, resolves conflicts the patchset way, reviews what changed, verifies and commits. Use when asked to update or rebase Telegram, bump the upstream version, or finish a stopped `patchset rebase`.
---

# Updating Neogram to a new Telegram release

Neogram is a patchset (see `AGENTS.md`, `docs/patchset.md`): `patchset.properties` pins the upstream
commit, `series` + `patches/` are the stack, `worktree/` is the upstream checkout with one commit per
patch. Never edit `patches/` or `series` by hand; everything goes through `tools/patchset.py`
(below `patchset` means `python tools/patchset.py`).

## 1. Before starting

- `git status` is clean, and `patchset status` shows the worktree in line with `series`
  (otherwise `patchset sync`, or `patchset sync --force` when the stack was rewritten).
- The current state builds and passes: `patchset lint`, `patchset check --sparse`.
- Note the current base: `patchset.properties` (`upstream.commit`, `upstream.version`).

## 2. Move the stack

```
patchset rebase latest        # or a commit sha / branch of TelegramOrg/Telegram-Android
```

The tool fetches the commit, updates submodules, stops Gradle daemons and rebases the stack one
patch at a time. On a conflict it stops at that patch:

1. Resolve the files in `worktree/`. Keep the patch's intent, not its old text:
   - re-anchor a hook on the new stock code (a guarded early return before the stock code, a call
     to fork code after it, a condition added to a stock expression);
   - never paste back old stock blocks, never re-indent stock code inside a new `if`;
   - fork code is called by fully qualified name (`ru.llacker.neogram...`), new stock members are
     `neo_*`, logic longer than ~7 lines belongs in `fork/`.
2. `git add` the files and run `patchset continue`. `patchset abort` restores the old stack.
3. If upstream now does what a patch did, drop it instead of resolving
   (`patchset drop <group/name>`), then remove its fork code, settings rows and strings. Keep the
   option key out of reuse: keys are stored on devices and in backups.
4. If the stock code a hook relied on is gone, write the hook again at the new place; when no
   sensible place is left, drop the feature and say so in the report.

At the end the tool rewrites `patchset.properties`, exports the stack and prints what happened to
each patch: *content changed* (read every one of these), *only moved*, *disappeared*.
`patchset changed [ref]` prints the same classification later.

## 3. Fork code against the new stock

Fork code (`fork/java`, `fork/app/java`) calls stock APIs directly, so upstream renames show up as
compile errors, not conflicts. Build and fix them in `fork/`, not with new stock patches. Areas
that use a lot of stock API and change often upstream:

- video messages: `InstantCameraView2`, `RoundVideoSession`, `RoundVideoCameraController`,
  `SharedSettings` (`ru.llacker.neogram.round`);
- settings hub and search: `SettingsActivity`, `ProfileActivity`, `UniversalFragment`/`UItem`;
- chat list: `DialogsActivity`, `FilterTabsView`, `MainTabsActivity`;
- chat screen: `ChatActivity` message menu, `ChatMessageCell`, `ChatActivityEnterView`;
- strings pipeline in `buildSrc` (`misc/strings-pipeline`).

## 4. Verify

```
python -m unittest discover -s tools/tests
patchset lint --strict
patchset check --sparse
worktree/gradlew -p fork/core test
cd worktree && ./gradlew :TMessagesProj_App:assembleAfatDebug -Pneo.abis=arm64-v8a --console=plain
```

- Removability: every feature/debloat/bugfix patch must still drop on its own, as in
  `.github/workflows/nightly.yml` (`patchset check --sparse --drop <group/name>` for each).
- When native code, ProGuard/R8 rules or the signature check could be affected, also build a
  signed release with a test keystore (`NEO_SIGNING_*` variables) and run
  `python tools/apkcheck.py signature <apk> --cert <cert.der>`.
- What cannot be checked without a device (camera, recording, notifications, login) goes into the
  report as unchecked.

## 5. Commit

Only this update's changes, message in Russian, lowercase subject describing the result, for
example `обновлена база telegram до 13.3.0`, and a body with: the upstream commit and version,
conflicts and how each was resolved, dropped or rewritten patches, fork code adjusted to new APIs,
and what was verified. Update `FEATURES.md` when a feature was dropped or became stock. Push only
when the task asks for it.
