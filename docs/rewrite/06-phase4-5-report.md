# Phases 4–5 report — review/fix pass and hardening

Emulator results below come from GitHub Actions (API 29/34/35, x86_64). They are not
physical-device results; those checks are listed in `docs/OWNER-MANUAL-TESTS.md`.

## 1. What was investigated

Phase 4 (review per feature, driven by emulator evidence)
- Music: tap-to-play from the Songs list, mini player (play/pause, next, close), MediaSession
  state + metadata (`dumpsys media_session`), media notification (title, actions, MediaStyle
  token, update on track change, kept while paused), queue restore after force-stop, history
  (Recently played / Most played on Home).
- Video: playback start, PiP on Home (auto-enter on API 31+, `onUserLeaveHint` below), PiP state
  in the UI, resume position saved, grid⇄list toggle, video favourite, Folders view.
- Library/Saved: favourite + undo snackbar, new playlist from the add-to-playlist sheet, Saved
  tab content, local search empty state.
- Settings: Light/Dark/System theme switch persisted; app following the system theme
  (night/day screenshots on API 34/35).
- Permission gate on a fresh install (API 29/34/35).
- Update path from the published v1.3.0 release (release-signed, same certificate).

Phase 5 (hardening)
- Lint (errors and warnings), StrictMode (main-thread disk/network, leaked closeables/SQLite
  objects/registrations, activity leaks), backup rules, crash handling, SQLite feature use
  against the oldest supported SQLite (Android 9/10 = 3.22).

## 2. What was rebuilt or changed

- Play history no longer uses SQLite UPSERT (crashed every play on Android 9/10); history writes
  are best-effort and cannot interrupt playback.
- PiP UI state also resyncs from window configuration and resume/pause.
- No app-side main-thread disk I/O left in startup or Settings (CrashLog lazy, crash reports read
  and cleared on IO).
- Backup rules: include-only (`mtplayer.db`, `mtplayer.db-wal`, settings DataStore); caches,
  crash logs and the read-only 1.x database are never backed up.
- Lint: 0 errors, now blocking in CI. Debug-only StrictMode (log, never crash), recorded per run.
- Emulator script: storage-ready wait, foreign ANR dialog dismissal, instrumentation logcat kept,
  crash detection limited to this package.

## 3. Files and commits

See `05-phase3-report.md` §3 (the review fixes landed in the same commits as the CI work):
`86281f1`, `ee0827a`, `7eaae21`, plus lint/StrictMode commits `c454c6e`, `e923217`, `a7120bf`.

## 4. Tests executed and results

- CI run 43732219946 on `7eaae21`: build, 26 unit tests, lint, emulator API 29/34/35
  (6/6 instrumentation each, 0 skipped, all shell checks), update over v1.3.0 — all pass.
- StrictMode, app frames only, same run: API 29 none; API 34/35 one disk read inside Media3
  `setMediaItems` during queue restore (must run on the player thread; accepted).
- Release build with R8: CI `update-compat` builds and installs the release-signed APK.

## 5. Remaining problems

- Physical-device-only items (OEM background limits, Bluetooth/car/wired-headset buttons,
  Android TV remote and leanback launcher, very large libraries, HDR/HEVC/4K files, low-RAM
  hardware): `docs/OWNER-MANUAL-TESTS.md`.
- Lint warnings left: dependency-version notices, two plural-candidate strings, one vector
  banner larger than 200 dp, the `mipmap-anydpi-v26` folder name.
- Owner action: delete the obsolete `MT_UPDATER_TOKEN` repository secret.

## 6. What comes next

Phase 6: PR `rewrite/v2 → main`, CI green on the PR, merge. The merge triggers `release.yml`,
which builds, verifies the signing certificate and publishes v2.0.0 (version code 7).
