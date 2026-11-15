# Next-agent handover — MT Player 2 rewrite

Keep this file current. Update it in every push so work can resume immediately after a crash
or usage limit. Read it first, then `docs/rewrite/00-phase0-audit.md`, `02-architecture.md`,
`03-feature-inventory.md` and the ADRs in `docs/rewrite/adr/`.

## Ground rules (from the owner)

- Full rewrite from scratch, not patches. Strictly local/offline media. The only network use is
  the opt-in updater (public GitHub Releases API, no token).
- Keep `applicationId = com.aistudio.mtplayer.kmzv`, signing certificate
  (`.github/release-signing-cert.sha256`), and user data (favourites, playlists, history).
- Never expose credentials. Signing secrets live only in GitHub secrets
  (`MT_KEYSTORE_BASE64`, `MT_KEYSTORE_PASSWORD`, `MT_KEY_ALIAS`, `MT_KEY_PASSWORD`).
  `MT_UPDATER_TOKEN` is obsolete and must be removed from `release.yml`.
- Work on branch `rewrite/v2`. Commit and push after every meaningful change. Do not wait for
  owner approval between phases. At the end: open PR `rewrite/v2 -> main`, CI green, merge
  (merging to `main` triggers the signed release via `release.yml`).
- Commit trailer: `Co-authored-by: Perplexity <noreply@perplexity.ai>`. PR/GitHub comments end
  with `<sub>Created with [Perplexity](https://www.perplexity.ai/computer/tasks/773a18ab-4d94-4f08-9ec7-eb3c4231591b)</sub>`.
- WIP commits that do not compile yet use `[skip ci]` in the message to avoid wasting CI runs.
- Do not claim physical-device verification from emulator results. Owner-only tests go in
  `docs/OWNER-MANUAL-TESTS.md`.

## Environment (sandbox)

- JDK 21 at `~/jdk`, Android SDK at `~/android-sdk` (platforms 36 / 36.1, build-tools 36.0.0).
  `local.properties` (git-ignored) has `sdk.dir`.
- Build: `ANDROID_HOME=~/android-sdk JAVA_HOME=~/jdk PATH=~/jdk/bin:$PATH ./gradlew --no-daemon assembleDebug testDebugUnitTest` (~5 min, 2 CPUs).
- TLS interception: if Gradle shows PKIX errors, import `/usr/local/share/ca-certificates/*.crt`
  into the JDK with `keytool -importcert -cacerts -storepass changeit -noprompt`.
- No KVM locally: emulator tests run only in GitHub Actions.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 0 | Audit, environment, executable wrapper | Done (`f2a08de`) |
| 1 | Research, architecture, feature inventory, ADRs 001–011 | Done (`c641d5f`) |
| 2 | Full rebuild: design system, data, DB + legacy import, playback, updater, shell, all screens, video player, logo, unit tests | Code complete, compiles, 25 unit tests pass locally and in CI (`bf96544`, run 43725855199 green) |
| 3 | Emulator CI: instrumentation tests (UI smoke, playback + MediaSession/notification, process death, PiP, permissions), install-over-v1.3.0 data migration test | Next |
| 4 | Review and fix pass per feature from emulator evidence (music, video, library, settings, TV/rail layout) | Planned |
| 5 | Hardening: R8 release build + keep rules, lint clean, StrictMode in debug, memory/ANR review, low-RAM paths | Planned |
| 6 | Docs (README, OWNER-MANUAL-TESTS, phase reports), remove `MT_UPDATER_TOKEN` from release.yml, PR `rewrite/v2 -> main`, CI green, merge | Planned |

The phase table was re-scoped after Phase 1: screens originally split across phases 3–6 were
built together in Phase 2 because they share one design system and navigation shell.

## Current state of `rewrite/v2`

The old `com.example` tree is deleted. All code is new in `app/src/main/java/com/mtplayer/app`
(namespace `com.mtplayer.app`, applicationId unchanged). `assembleDebug` and `testDebugUnitTest`
pass locally.

- `designsystem/theme` — Tide palette, `Contrast` (WCAG; `ensure` tries both directions),
  `Theme` (dark/light/pure-black, guarded dynamic colour, `resolveDark`, `MtTheme`), `Type`.
- `designsystem/components` — Artwork, States (loading/message), Feedback (Messenger snackbar,
  dialogs, action/choice sheets), Rows (MediaRow, VideoCard, TileCard, PlayingBars…).
- `core`, `data` (MediaStore → `LibraryIndex`, Room `mtplayer.db` v1 with exported schema in
  `app/schemas`, `LegacyImporter`, DataStore settings, lyrics), `playback` (MediaLibraryService +
  notification + sleep timer + queue persistence), `update` (token-free, SHA-256 + signer check).
- `feature/shell` — MainActivity (splash, edge-to-edge, audio VIEW intents), AppRoot (bottom bar /
  rail ≥600dp, mini player with close + swipe dismiss, permission gate), Navigation, MediaActions
  (song/video sheets, add-to-playlist, favourites with undo).
- `feature/*` — Home, Music (6 tabs), Videos (grid/list, folders), Saved, Search, detail screens,
  Settings (+ hidden folders, crash reports), UpdatePrompt, Equalizer, Now Playing (lyrics, queue,
  sleep, speed).
- `video/` — VideoPlayerActivity (own ExoPlayer + MediaSession id `mt_video`, resume, PiP with
  remote actions/auto-enter, background audio hand-off, subtitles via SAF) and VideoPlayerScreen
  (gestures, fit modes, lock, track/speed sheets).
- Tests in `app/src/test`: ThemeContrastTest, LrcParserTest, LibraryIndexTest (Robolectric),
  UpdateManagerTest, CrashLogTest, LegacyImporterTest (Robolectric, synthetic v1 DB).

## Exact next steps

1. Confirm CI run for `bf96544` is green (`gh run list --branch rewrite/v2 --limit 3`). Fix if not.
2. Write `docs/rewrite/04-phase2-report.md` (investigated / rebuilt / files+commits / tests / remaining / next).
3. Phase 3: add `app/src/androidTest` (Compose UI smoke: launch, tabs, permission gate, settings theme
   switch; MediaController test: service starts, metadata, notification posted via
   NotificationManager.activeNotifications; Room in-device test) and a CI job
   `emulator` using `reactivecircus/android-emulator-runner@v2` (API 29 + 34 + 35, x86_64, KVM enabled
   via udev rule), plus an install-over-v1.3.0 job: download the v1.3.0 release APK, install, seed
   data is not possible without root, so verify `adb install -r` of the new debug-signed build is
   rejected only by signature (release signing is verified in release.yml) and that a debug build
   imports a pushed synthetic `melody_player_local.db` via `run-as`.
4. Phases 4–6 per the table.

## Known issues / risks

- Not yet verified on any emulator or device; everything beyond unit tests is unverified.
- `Settings.artworkTint` and `gapless` are imported from v1 but not shown in UI (ExoPlayer is
  gapless by default; no misleading toggle).
- Lint runs with `continue-on-error` in CI; must be reviewed in Phase 5.

## Latest commit

See `git log -1` on `rewrite/v2`. This file is updated in the same commit as the work it describes.
