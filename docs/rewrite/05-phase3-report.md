# Phase 3 report — emulator CI, update compatibility, first hardening

Status: **done** — all CI jobs green on `7eaae21` (run 43732219946). See the results table; it is filled from GitHub Actions runs only. Emulator results
are not physical-device results (those are listed in `docs/OWNER-MANUAL-TESTS.md`).

## 1. What was investigated

- Whether the rebuilt app actually works on real Android system images, not only in unit tests:
  Android 10 (API 29, the oldest SQLite/notification behaviour we support in CI), Android 14
  (API 34) and Android 15 (API 35), `google_apis` x86_64, Pixel 6 profile, KVM-accelerated.
- Whether 2.0.0 installs as an **update** over the published 1.3.0 release (same package, same
  signing certificate) and imports the 1.x user data without touching the old database.
- StrictMode output (debug builds) for main-thread disk/network work and leaks.
- Lint errors.

## 2. What was rebuilt or changed

Tests and CI
- `app/src/androidTest` (6 tests): `AppUiTest` (tabs, settings theme switch persisted for Light/Dark/System,
  local search empty state; play a song from the UI, notification, next track, favourite,
  new playlist, Saved tab, close the mini player; videos grid⇄list toggle, video favourite, folders),
  `PlaybackSessionTest` (MediaController →
  PlaybackService: playback, MediaStyle notification with title, ≥3 actions, session token,
  updates on track change, stays while paused, shuffle/repeat/seek), `VideoPlayerTest` (video
  plays, Home enters PiP, PiP state reaches the UI, resume position saved on exit).
- `.github/scripts/emulator-tests.sh`: pushes generated media, checks the permission gate on a
  fresh install, runs instrumentation, plays from the UI via uiautomator taps and checks
  `dumpsys media_session` / `dumpsys notification`, force-stops and checks the queue is restored,
  captures night/day screenshots, fails on any FATAL/ANR for the app in logcat, records
  StrictMode violations. Waits for shared storage after boot and dismisses foreign system
  "isn't responding" dialogs (kept as screenshots).
- `.github/scripts/update-compat.sh`: installs the real `release-MT-Player-v1.3.0.apk`, seeds
  1.x data with sqlite3, `adb install -r` of the release-signed 2.0.0 build (signing cert checked
  against `.github/release-signing-cert.sha256`), verifies imported favourites, playlist order,
  resume point, skipped placeholder playlists, untouched 1.x DB (md5), idempotency, no crash.
- CI lint is now blocking (0 errors).

App fixes found by this phase
- **Crash on Android 9/10**: play history used `INSERT … ON CONFLICT DO UPDATE` (SQLite UPSERT,
  3.24+). Android 9/10 ship SQLite 3.22, so every song or video start crashed. Replaced with
  insert-or-ignore + update in one transaction; history writes are now best-effort and can never
  stop playback. New unit test `HistoryDaoTest`.
- Main-thread disk I/O removed: CrashLog no longer touches the disk in `Application.onCreate`;
  Settings reads/clears crash reports on `Dispatchers.IO`.
- Lint errors fixed: backup rules (include-only, also `mtplayer.db-wal`), UnstableApi opt-in for
  video resize modes, `already_in_playlist` format argument, obsolete SDK checks.
- Debug-only StrictMode (log, never crash).

## 3. Files and commits

`c454c6e` lint fixes · `66ef1ec` theme test asserts persisted mode, lint blocking · `e923217` StrictMode
· `a7120bf` obsolete SDK checks (mipmap move reverted in `86281f1`) · `86281f1` UPSERT crash fix + test fixes + ANR
dialog handling · `ee0827a` main-thread I/O fixes + storage wait · video grid/list/favourite/folders
UI test · `7eaae21` PiP UI state resync from window configuration and lifecycle, instrumentation
logcat kept, app-only crash matching. Earlier Phase 3 setup:
`a21f6af`, `f7520ca`.

## 4. Tests executed and results

| Run | Commit | Build/unit/lint | API 29 | API 34 | API 35 | Update over 1.3.0 |
|---|---|---|---|---|---|---|
| 43726937809 | f7520ca | Fail (Maven Central HTTP 429, transient) | skipped | skipped | skipped | skipped |
| 43727256725 | c454c6e | Pass | Fail: UPSERT crash | Fail: launcher ANR dialog over gate | Fail: test bugs (last-item command, PiP race) | **Pass** |
| 43728953056 | 86281f1 | Pass | **Pass** (5/5 instrumentation, all shell checks) | **Pass** (5/5, all shell checks) | Fail: shared storage not ready after boot (infra) | **Pass** |
| 43730533412 | ee0827a | Pass | **Pass** | **Pass** | Fail: PiP UI flag not set within 5 s | **Pass** |
| **43732219946** | **7eaae21** | **Pass** | **Pass** (6/6, 0 skipped) | **Pass** (6/6, 0 skipped) | **Pass** (6/6, 0 skipped) | **Pass** |

PiP: the platform callback fired on API 29, 34 and 35 in the green run (logcat `MtVideo`), and
the window-configuration resync covers the earlier late delivery.

Unit tests: 26 (7 classes), all pass locally and in CI.

Evidence artifacts per run: `emulator-evidence-api<N>` (summary, screenshots, UI dumps, dumpsys
media_session/notification, logcat, StrictMode, JUnit XML) and `update-compat-evidence`.

## 5. Remaining problems

- The API 29 emulator image ignores `cmd uimode night yes` (its own system bars stay light), so
  the API 29 "night" screenshot does not show dark mode; system dark mode is evidenced on API 34
  and 35, and in-app Light/Dark/System switching is tested on all three.
- ExoPlayer `setMediaItems` on restore logs one small StrictMode disk read (inside Media3, must run
  on the player thread); accepted.
- Physical-device behaviour (OEM battery killers, Bluetooth/car controls, Android TV remote,
  real 4K/HEVC files) cannot be proven on emulators; see `docs/OWNER-MANUAL-TESTS.md`.

## 6. What comes next

Phase 4 review pass per feature using this evidence, Phase 5 memory/low-RAM review, Phase 6 PR
`rewrite/v2 → main` with green CI and merge (which publishes the signed 2.0.0 release).
